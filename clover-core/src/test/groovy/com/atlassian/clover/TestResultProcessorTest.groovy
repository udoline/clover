package com.atlassian.clover

import com.atlassian.clover.api.CloverException
import com.atlassian.clover.api.registry.ClassInfo
import com.atlassian.clover.registry.entities.FullClassInfo
import com.atlassian.clover.registry.metrics.HasMetricsFilter
import com.atlassian.clover.registry.entities.FullProjectInfo
import com.atlassian.clover.registry.entities.TestCaseInfo
import com.atlassian.clover.testutils.IOHelper
import com.atlassian.clover.util.FileUtils
import junit.framework.TestCase

import static org.openclover.util.Lists.newArrayList;
import static org.openclover.util.Lists.newLinkedList;
import static org.openclover.util.Sets.newHashSet

/**
 * This tests parsing TEST xml files.
 * The files should be in the testresults directory, relative
 * to this package.
 */
class TestResultProcessorTest extends TestCase {

    public static String PATH_TO_TEST_FILES = "clover-core/src/test/resources/testresults"

    List testResultFiles; 
    List testSuiteFiles = newArrayList();
    CloverDatabase db
    File workingDir
    File projectDir

    void setUp() throws IOException, CloverException {
        projectDir = IOHelper.getProjectDir()
        workingDir = IOHelper.createTmpDir(TestResultProcessorTest.class.getName())

        CloverTestFixture testFixture = new CloverTestFixture(workingDir)
        final String initString = testFixture.createCoverageDB()
        final List classList = newLinkedList()
        final CloverTestFixture.Coverage cvg = new CloverTestFixture.Coverage(1, 1, 1, 4)
        classList.add(new CloverTestFixture.Clazz(workingDir, "com.cenqua.test", "Test1", cvg))
        classList.add(new CloverTestFixture.Clazz(workingDir, "com.cenqua.test", "Test2", cvg))
        classList.add(new CloverTestFixture.Clazz(workingDir, "com.cenqua.test", "Test4", cvg))
        classList.add(new CloverTestFixture.Clazz(workingDir, "com.cenqua.test", "Test5", cvg))
        classList.add(new CloverTestFixture.Clazz(workingDir, "", "Test3", cvg))
        testFixture.register(initString, classList)

        db = new CloverDatabase(initString, HasMetricsFilter.ACCEPT_ALL, "name")
        db.loadCoverageData(new CoverageDataSpec(HasMetricsFilter.ACCEPT_ALL, 0))

        final File resultsDir = new File(
                FileUtils.getPlatformSpecificPath(projectDir.getAbsolutePath() + File.separator + PATH_TO_TEST_FILES))
        assertTrue("The '" + resultsDir + "' is not a directory", resultsDir.isDirectory())
        final File[] files = resultsDir.listFiles(new FilenameFilter() {
            boolean accept(File file, String string) {
                return string.contains("TEST-")
            }
        })

        File suiteResults = new File(resultsDir, "TESTS-TestSuite.xml")

        testResultFiles = newArrayList(files)
        testSuiteFiles.add(suiteResults)
    }

    void tearDown() throws Exception {
        IOHelper.delete(workingDir)
    }


    void testParseTestSuiteXML() throws Exception {
        final FullProjectInfo testModel = db.getTestOnlyModel()
        TestResultProcessor.addTestResultsToModel(testModel, testSuiteFiles)
        assertTestResults(testModel)
        FullClassInfo t3 = (FullClassInfo) testModel.findClass("Test3")

        TestCaseInfo failedTest = t3.getTestCase("Test3.methodFailure")
        assertTrue(failedTest.isFailure())
        assertEquals("This test failed.", failedTest.getFailMessage())

        TestCaseInfo errorTest = t3.getTestCase("Test3.methodError")
        assertTrue(errorTest.isError());        
        assertEquals("This test had an error.", errorTest.getFailMessage())

    }

    void testParseTestFiles() throws Exception {
        final FullProjectInfo testModel = db.getTestOnlyModel()
        TestResultProcessor.addTestResultsToModel(testModel, testResultFiles)
        assertTestResults(testModel)
    }

    private void assertTestResults(FullProjectInfo testModel) {
        final FullClassInfo test1 = (FullClassInfo) testModel.findClass("com.cenqua.test.Test1")
        final FullClassInfo test2 = (FullClassInfo) testModel.findClass("com.cenqua.test.Test2")
        final FullClassInfo test4 = (FullClassInfo) testModel.findClass("com.cenqua.test.Test4")
        final FullClassInfo test5 = (FullClassInfo) testModel.findClass("com.cenqua.test.Test5")
        final FullClassInfo t3 = (FullClassInfo) testModel.findClass("Test3")
        assertTestClass(test1)
        assertTestClass(test2)
        assertTestClass(test4)
        assertTestClass(test5)
        assertTestClass(t3)

        Map<FullClassInfo, Double> testTimes = new HashMap<FullClassInfo, Double>() {{
            put(test1, 10.0d)
            put(test2, 20.22d)
            put(t3, 200.2d)
            put(test4, 30.333d)
            put(test5, 30.333d)
        }}
        
        Map<FullClassInfo, Set<Integer>> testMethods = new HashMap<FullClassInfo, Set<Integer>>() {{
            put(test1, newHashSet(0, 1, 2))
            put(test2, newHashSet(0, 1, 2))
            put(t3, newHashSet(0, 1, 2))
            put(test4, newHashSet(0, 1))
            put(test5, newHashSet(2))
        }}

        for (FullClassInfo classInfo : [ test1, test2, t3, test4 ]) {
            for (Integer testNum : testMethods.get(classInfo)) {
                String methodName = "method" + testNum
                TestCaseInfo tci = classInfo.getTestCase("${classInfo.qualifiedName}.${methodName}".toString())
                assertNotNull("No test case called ${classInfo.qualifiedName}.${methodName}".toString(), tci)
                assertEquals(tci.getTestName(), "method" + testNum)
                assertEquals(tci.getDuration(), testTimes.get(classInfo), 1.0)
            }
        }
    }


    private void assertTestClass(ClassInfo test1) {
        assertTrue(test1.getQualifiedName() + " not marked as test class", test1.isTestClass())
    }

}
