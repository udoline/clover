package com.atlassian.clover.ant.tasks

import groovy.transform.CompileStatic

@CompileStatic
class CloverOptimizeJUnitSrcFilesetFuncTest extends CloverOptimizeJUnitFilesetTestBase {
    CloverOptimizeJUnitSrcFilesetFuncTest(String name) {
        super(
            name,
            "runJUnitSourceFileSetTests",
            [
                "testOptimizedCIBuildCyclesWithAlwaysRunTests" : "runJUnitSourceFileSetTestsWithAlwaysRunTests",
                "testOptimizedCIBuildCyclesWithMixedOptimizedTests" : "runJUnitSourceFileSetTestsWithMixedOptimizedTests"
            ])
    }

}
