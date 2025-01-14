package com.atlassian.clover;

import com.atlassian.clover.api.CloverException;
import com.atlassian.clover.context.ContextSet;
import com.atlassian.clover.registry.FixedSourceRegion;
import com.atlassian.clover.registry.entities.BasicMethodInfo;
import com.atlassian.clover.registry.entities.FullClassInfo;
import com.atlassian.clover.registry.entities.FullProjectInfo;
import com.atlassian.clover.registry.entities.FullMethodInfo;
import com.atlassian.clover.registry.entities.MethodSignature;
import com.atlassian.clover.registry.entities.TestCaseInfo;
import com.atlassian.clover.util.CloverUtils;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class TestResultProcessor {

    /**
     * @param model the model to add the test results to
     * @param files the TEST result XML files
     * @throws com.atlassian.clover.api.CloverException if an error occurs parsing the XML
     */
    public static void addTestResultsToModel(FullProjectInfo model, List<File> files) throws CloverException {
        TestResultProcessor processor = new TestResultProcessor(model, files);
        int numTestResults = processor.scan();
        model.setHasTestResults(numTestResults > 0);
    }

    private FullProjectInfo model;
    private List<File> files;


    public TestResultProcessor(FullProjectInfo model, List<File> files) {
        this.model = model;
        this.files = files;
    }

    /**
     * @return the number of test results processed
     */
    public int scan() throws CloverException {
        Logger log = Logger.getInstance();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            TestXMLHandler handler = new TestXMLHandler(model);
            int fileCount = 0;
            int testCaseCount = 0;
            for (File file : files) {
                try {
                    log.verbose("Processing test result file " + file);
                    parser.parse(new BufferedInputStream(Files.newInputStream(file.toPath())), handler);
                    fileCount++;
                    testCaseCount += handler.getTestCaseCount();
                } catch (IOException e) {
                    log.error("IOException parsing " + file + ", skipped", e);
                } catch (SAXException e) {
                    log.error("SAXException parsing " + file + ", skipped", e);
                }
            }
            log.info("Processed " + testCaseCount + " test case result" + (testCaseCount != 1 ? "s" : "") +
                " from " + fileCount + " results file" + (fileCount != 1 ? "s" : "") + ".");
            return testCaseCount;
        }
        catch (ParserConfigurationException e) {
            throw new CloverException("ParserConfigurationException configuring XML Parser: " + e.getMessage());
        }
        catch (SAXException e) {
            throw new CloverException("SAXException obtaining XML Parser: " + e.getMessage());
        }
    }


    static class TestXMLHandler extends DefaultHandler {
        private FullProjectInfo model;
        private FullClassInfo currentTestClassFromTestSuite;
        private FullClassInfo currentTestClassFromTestCase;
        private TestCaseInfo currentTestCaseInfo;
        private StringBuffer message;
        private int testCaseCount;

        public TestXMLHandler(FullProjectInfo model) {
            this.model = model;
        }

        @Override
        public void startDocument() {
            testCaseCount = 0;
        }


        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
            if ("testsuite".equals(qName)) {
                // NOTE: testsuite also has the following attributes, currently unused by clover:
                // errors="0" failures="0" hostname="nixx" tests="32" time="0.401" timestamp="2007-02-05T00:40:18"
                String classnameAttr = atts.getValue("name");
                FullClassInfo classInfo = null;

                if (classnameAttr != null) { // handles the case the nameAttr is fully qualified classname. e.g. from a TEST-pkg.Test.xml
                    classInfo = findClass(classnameAttr);
                }

                String packageAttr = atts.getValue("package");
                if ((classInfo == null) && (packageAttr != null) && (classnameAttr != null)) { // case from a TESTS-TestSuite.xml when a package attribute is available
                    packageAttr = packageAttr.trim().length() == 0 ? "" : packageAttr + ".";
                    final String fqnClassname = packageAttr + classnameAttr;
                    classInfo = findClass(fqnClassname);
                }

                if (classInfo != null) {
                    currentTestClassFromTestSuite = classInfo;
                }
            } else if ("testcase".equals(qName)) {
                String classname = atts.getValue("classname");
                String testname = atts.getValue("name");
                float time = 0.0f;
                try {
                    time = Float.parseFloat(atts.getValue("time"));
                } catch (NumberFormatException e) {
                    //TODO: might log a debug
                }

                if (classname != null) {
                    classname = CloverUtils.cloverizeClassName(classname); // hack - see CCD-294, CCD-307
                    currentTestClassFromTestCase = (FullClassInfo) model.findClass(classname);
                } else {
                    currentTestClassFromTestCase = currentTestClassFromTestSuite;
                    // the test name might be fully qualified (ie. with fq test classname preprended to method name)
                    if (testname != null) {
                        int lastDot = testname.lastIndexOf(".");
                        if (lastDot >= 0) {
                            classname = CloverUtils.cloverizeClassName(testname.substring(0, lastDot)); // hack - see CCD-294, CCD-307
                            currentTestClassFromTestCase = (FullClassInfo) model.findClass(classname);
                            testname = testname.substring(lastDot + 1);
                        }
                    }
                }

                FullClassInfo currentTestClass = currentTestClassFromTestCase == null ? currentTestClassFromTestSuite : currentTestClassFromTestCase;
                if (currentTestClass != null) {
                    currentTestCaseInfo = currentTestClass.getTestCase(currentTestClass.getQualifiedName() + "." + testname);
                    if (currentTestCaseInfo == null) {
                        Logger.getInstance().verbose(
                            "Didn't find pre-existing test case for class from JUnit results: " + currentTestClass.getQualifiedName() + "." + testname);
                        // look for the method declaration for this testcase
                        FullMethodInfo methodDecl = currentTestClass.getTestMethodDeclaration(testname);
                        // look on the testsuite class if not found on the testcase
                        if (methodDecl == null && (currentTestClass == currentTestClassFromTestCase) && (currentTestClassFromTestSuite != null)) {
                            methodDecl = currentTestClassFromTestSuite.getTestMethodDeclaration(testname);
                            if (methodDecl != null) {
                                currentTestClass = currentTestClassFromTestSuite;
                            }
                        }

                        if (methodDecl != null) {
                            // generate negative slice id from a qualified method name
                            currentTestCaseInfo = new TestCaseInfo(-Math.abs(methodDecl.getQualifiedName().hashCode()),
                                    currentTestClass, methodDecl, methodDecl.getSimpleName());
                        } else {
                            // generate negative slice id from a test name using fake method
                            FullMethodInfo fakeTestMethod = new FullMethodInfo(currentTestClass, new ContextSet(),
                                    new BasicMethodInfo(new FixedSourceRegion(0, 0), 0, 0,
                                            new MethodSignature(testname), true, testname, false));
                            currentTestCaseInfo = new TestCaseInfo(-Math.abs(testname.hashCode()),
                                    currentTestClass, fakeTestMethod, testname);
                        }
                        currentTestClass.addTestCase(currentTestCaseInfo);
                    } else {
                        Logger.getInstance().verbose("Found pre-existing test case for class from JUnit results: " + currentTestClass.getQualifiedName() + "." + testname + " ; isSuccess = " + currentTestCaseInfo.isSuccess());
                    }
                    if (currentTestCaseInfo != null) {
                        testCaseCount++;
                        currentTestCaseInfo.setHasResult(true);
                        currentTestCaseInfo.setError(false);
                        currentTestCaseInfo.setFailure(false);
                        currentTestCaseInfo.setFailMessage(null);
                        currentTestCaseInfo.setFailFullMessage(null);
                        currentTestCaseInfo.setFailType(null);
                        currentTestCaseInfo.setDuration(time);
                    }
                } else {
                    Logger.getInstance().verbose(
                        "Couldn't find test class for <testsuite/> or <testcase/> to annotate with JUnit results: " + classname + "." + testname);
                }
            } else if (("failure".equals(qName) || "error".equals(qName)) && currentTestCaseInfo != null) {
                if ("error".equals(qName)) {
                    currentTestCaseInfo.setError(true);
                } else {
                    currentTestCaseInfo.setFailure(true);
                }
                currentTestCaseInfo.setFailMessage(atts.getValue("message"));
                currentTestCaseInfo.setFailType(atts.getValue("type"));
                message = new StringBuffer();
            }
        }

        /**
         * Cloverizes and then looks up a ClassInfo object from the model,
         * given a raw classname.
         *
         * @param rawName raw name of the class
         * @return class found in the model or null
         */
        @Nullable
        private FullClassInfo findClass(String rawName) {
            final String classname = CloverUtils.cloverizeClassName(rawName); // hack - see CCD-294, CCD-307
            final FullClassInfo info = (FullClassInfo)model.findClass(classname);
            Logger.getInstance().debug("Found class: " + info + " using name " + rawName);
            return info;
        }


        @Override
        public void characters(char[] ch, int start, int length) {
            collectMessage(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) {
            collectMessage(ch, start, length);
        }

        private void collectMessage(char[] ch, int start, int length) {
            if (currentTestCaseInfo != null && message != null) {
                message.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String namespaceURI, String localName, String qName) {
            if ("testsuite".equals(qName)) {
                currentTestClassFromTestSuite = null;
            } else if ("testcase".equals(qName)) {
                currentTestClassFromTestCase = null;
                currentTestCaseInfo = null;
            } else if (currentTestCaseInfo != null && ("failure".equals(qName) || "error".equals(qName)) && message != null) {
                currentTestCaseInfo.setFailFullMessage(message.toString());
                message = null;
            }
        }

        @Override
        public void endDocument() {
            Logger.getInstance().verbose("Processed " + testCaseCount + " test case result" + (testCaseCount != 1 ? "s" : "") + ".");
        }

        public int getTestCaseCount() {
            return testCaseCount;
        }
    }
}
