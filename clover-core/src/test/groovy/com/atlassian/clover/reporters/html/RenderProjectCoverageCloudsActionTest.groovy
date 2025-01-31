package com.atlassian.clover.reporters.html

import com.atlassian.clover.api.registry.BranchInfo
import com.atlassian.clover.registry.entities.FullStatementInfo
import com.atlassian.clover.reporters.CloverReportConfig
import com.atlassian.clover.reporters.Current
import junit.framework.TestCase
import clover.org.apache.velocity.VelocityContext

import com.atlassian.clover.registry.entities.FullProjectInfo
import com.atlassian.clover.registry.entities.FullClassInfo
import com.atlassian.clover.registry.metrics.HasMetricsTestFixture
import com.atlassian.clover.registry.entities.FullMethodInfo
import com.atlassian.clover.context.ContextSet
import com.atlassian.clover.context.ContextStore

import static org.openclover.util.Lists.newArrayList

/**
 */
class RenderProjectCoverageCloudsActionTest extends TestCase {

    private VelocityContext ctx
    private HtmlReporter.TreeInfo tree
    private List classes
    private File basePath
    private FullProjectInfo projInfo
    private HasMetricsTestFixture fixture

    private BranchInfo branchInfo
    private FullStatementInfo stmtInfo
    private FullClassInfo classInfo
    private FullMethodInfo methodInfo
    private CloverReportConfig reportConfig

    /**
     * Sets up a FullProjectInfo model object containing the following:
     * 1 PackageInfo - "testpkg"
     * 1 FileInfo - "testpkg.Test.java"
     * 1 ClassInfo - "testpkg.Test"
     * 1 MethodInfo - "testpkg.Test#method1"
     * 1 StatementInfo - "testpkg.Test#method1 { cmp = 1, startline = 3, hitCount = 0, context = "assert"; }"
     * 1 BranchInfo - "testpkg.Test#method1    { cmp = 2, startline = 4, hitCount = 1, context = "if"; }"
     * @throws java.io.IOException
     */
    protected void setUp() throws IOException {
        ctx = new VelocityContext()
        reportConfig = new Current()
        tree = new HtmlReporter.TreeInfo("pathPrefix", "name")
        classes = newArrayList()
        basePath = new File("pathName")

        fixture = new HasMetricsTestFixture(this.getClass().getName())
        classInfo =  fixture.newClass("Test", 1); //class on line1
        methodInfo = fixture.newMethod(classInfo, "method1", 2); // method on line2
        stmtInfo =   fixture.addStatement(methodInfo, 1, 3, 0); // statement on line3, cmp = 1, hitcoutnt = 0
        ContextSet assertCtx = new ContextSet().set(ContextStore.CONTEXT_ASSERT)
        stmtInfo.setContext(assertCtx)

        ContextSet ifCtx = new ContextSet().set(ContextStore.CONTEXT_IF)
        branchInfo = fixture.addBranch(methodInfo, ifCtx, 4, 1); // branch on line4,
    }

    void testApplySpecificProperties() {
        projInfo = fixture.getProject()
        ContextSet filter = new ContextStore().createContextSetFilter("assert")
        projInfo.setContextFilter(filter)

        RenderProjectCoverageCloudsAction action = new RenderProjectCoverageCloudsAction(
                ctx, reportConfig, basePath, tree, projInfo, classes)
        action.applySpecificProperties()

        assertEquals(projInfo, ctx.get("projectInfo"))
        assertEquals(projInfo.getMetrics(), ctx.get("headerMetrics"))
        assertEquals(projInfo.getRawMetrics(), ctx.get("headerMetricsRaw"))
        assertEquals(Boolean.TRUE, ctx.get("appPagePresent"))
        assertEquals(Boolean.TRUE, ctx.get("testPagePresent"))
        assertEquals(Boolean.TRUE, ctx.get("topLevel"))
        assertEquals("25%", ctx.get("percentFiltered"))
    }

}
