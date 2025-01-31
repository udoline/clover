package com.atlassian.clover.reporters

import com.atlassian.clover.api.registry.MethodInfo
import com.atlassian.clover.context.ContextSet
import com.atlassian.clover.registry.FixedSourceRegion
import com.atlassian.clover.registry.entities.BasicMethodInfo
import com.atlassian.clover.registry.entities.FullClassInfo
import com.atlassian.clover.registry.entities.FullMethodInfo
import com.atlassian.clover.registry.entities.MethodSignature
import com.atlassian.clover.registry.metrics.BlockMetrics
import com.atlassian.clover.registry.metrics.HasMetricsTestFixture
import com.atlassian.clover.registry.metrics.MetricsHelper
import junit.framework.TestCase

import static org.openclover.util.Lists.newArrayList

class MetricsCollatorTest extends TestCase {
    List<FullClassInfo> classes = newArrayList()
    List<FullClassInfo> classes2 = newArrayList()
    HasMetricsTestFixture fixture

    MetricsCollatorTest() {
    }

    void setUp() throws IOException {
        fixture = new HasMetricsTestFixture("test")
        FullClassInfo info = fixture.newClass("Test1", 1)
        addMethod(info, 2, 2, 2); // 50% sixth testMethod0
        addMethod(info, 2, 3, 3); // 50% fifth testMethod1
        addMethod(info, 3, 3, 3); // 50% fourth testMethod0
        addMethod(info, 2, 3, 6); // 33% third testMethod2
        addMethod(info, 2, 3, 12); // 20% second testMethod3
        addMethod(info, 3, 3, 12); // 20% first testMethod4
        classes.add(info)

        // class Test2
        // + foo
        //   + inner
        //   + $lam#2
        // + $lam#1

        final FullClassInfo info2 = fixture.newClass("Test2", 1)
        // normal method
        final FullMethodInfo fooMethod = new FullMethodInfo(
                info2,
                new ContextSet(),
                new BasicMethodInfo(new FixedSourceRegion(2, 1), 0, 10, new MethodSignature("foo"), false, null, false)
        )
        fooMethod.setMetrics(MetricsHelper.setBlockMetrics(new BlockMetrics(fooMethod), 10, 9, 0, 0, 2, 0, 0, 0, 0, 0.0f))

        // lambda method
        final FullMethodInfo lam1Method = new FullMethodInfo(
                info2,
                new ContextSet(),
                new BasicMethodInfo(new FixedSourceRegion(3, 1), 0, 10, new MethodSignature('$lam#1'), false, null, true)
        )
        lam1Method.setMetrics(MetricsHelper.setBlockMetrics(new BlockMetrics(lam1Method), 10, 8, 0, 0, 2, 0, 0, 0, 0, 0.0f))

        // inner method
        final FullMethodInfo innerMethod = new FullMethodInfo(
                fooMethod,
                new ContextSet(),
                new BasicMethodInfo(new FixedSourceRegion(4, 1), 0, 10, new MethodSignature("inner"), false, null, false)
        )
        innerMethod.setMetrics(MetricsHelper.setBlockMetrics(new BlockMetrics(innerMethod), 10, 7, 0, 0, 2, 0, 0, 0, 0, 0.0f))

        // inner lambda method
        final FullMethodInfo lam2Method = new FullMethodInfo(
                fooMethod,
                new ContextSet(),
                new BasicMethodInfo(new FixedSourceRegion(5, 1), 0, 10, new MethodSignature('$lam#2'), false, null, true)
        )
        lam2Method.setMetrics(MetricsHelper.setBlockMetrics(new BlockMetrics(lam2Method), 10, 6, 0, 0, 2, 0, 0, 0, 0, 0.0f))

        info2.addMethod(fooMethod)
        info2.addMethod(lam1Method)
        info2.addMethod(innerMethod)
        info2.addMethod(lam2Method)
        classes2.add(info2)
    }

    private void addMethod(FullClassInfo info, int stmtComplexity, int numCoveredStmts, int numUncoveredStmts) {

        FullMethodInfo method = fixture.newMethod(info,
                                              "testMethod" + info.getMethods().size(),
                                              info.getMethods().size() + 1)

        addStatements(numCoveredStmts, method, stmtComplexity, 1)
        addStatements(numUncoveredStmts, method, stmtComplexity, 0)

    }

    private void addStatements(int count, FullMethodInfo method, int stmtComplexity ,int hitCount) {
        for (int i = 0; i < count; i++) {
            fixture.addStatement(method, stmtComplexity, i + 1, hitCount)
        }
    }

    void testAddLeastTestedMethods() {
        MetricsCollator col = new MetricsCollator()
        List<MethodInfo> methods = col.getLeastTestedMethods(classes, false, false)
        assertEquals(46, methodAt(methods, 0).getComplexity())
        assertEquals(15, methodAt(methods, 0).getNumElements())
        assertEquals(12, methodAt(methods, 0).getNumUncoveredElements())
        assertEquals("testMethod5()", methodAt(methods, 0).getOwner().getName())
        assertEquals("testMethod4()", methodAt(methods, 1).getOwner().getName())
        assertEquals("testMethod3()", methodAt(methods, 2).getOwner().getName())
        assertEquals("testMethod2()", methodAt(methods, 3).getOwner().getName())
        assertEquals("testMethod1()", methodAt(methods, 4).getOwner().getName())
        assertEquals("testMethod0()", methodAt(methods, 5).getOwner().getName())
    }

    void testGetLeastTestedMethods() {
        MetricsCollator col = new MetricsCollator()
        List<MethodInfo> methods = col.getLeastTestedMethods(classes2, false, false)
        assertEquals(1, methods.size())
        assertEquals("foo()", methods.get(0).getName())
    }

    void testGetLeastTestedMethodsWithLambda() {
        MetricsCollator col = new MetricsCollator()
        List<MethodInfo> methods = col.getLeastTestedMethods(classes2, true, false)
        assertEquals(2, methods.size())
        assertEquals('$lam#1()', methods.get(0).getName())
        assertEquals("foo()", methods.get(1).getName())
    }

    void testGetLeastTestedMethodsWithInner() {
        MetricsCollator col = new MetricsCollator()
        List<MethodInfo> methods = col.getLeastTestedMethods(classes2, false, true)
        assertEquals(2, methods.size())
        assertEquals("inner()", methods.get(0).getName())
        assertEquals("foo()", methods.get(1).getName())
    }

    void testGetLeastTestedMethodsWithLambdaAndInner() {
        MetricsCollator col = new MetricsCollator()
        List<MethodInfo> methods = col.getLeastTestedMethods(classes2, true, true)
        assertEquals(4, methods.size())
        assertEquals('$lam#2()', methods.get(0).getName())
        assertEquals("inner()", methods.get(1).getName())
        assertEquals('$lam#1()', methods.get(2).getName())
        assertEquals("foo()", methods.get(3).getName())
    }

    com.atlassian.clover.api.registry.BlockMetrics methodAt(List<MethodInfo> methods, int index) {
        return methods.get(index).getMetrics()
    }

}
