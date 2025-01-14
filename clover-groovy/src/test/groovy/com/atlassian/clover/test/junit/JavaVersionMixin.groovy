package com.atlassian.clover.test.junit

import com.atlassian.clover.util.JavaEnvUtils
import com.atlassian.clover.util.collections.Pair
import groovy.transform.CompileStatic

import static com.atlassian.clover.util.JavaEnvUtils.JAVA_11
import static com.atlassian.clover.util.JavaEnvUtils.JAVA_17
import static com.atlassian.clover.util.JavaEnvUtils.JAVA_8

@CompileStatic
trait JavaVersionMixin {

    boolean shouldRunInCurrentJava(String groovyVersion) {
        /** groovy version prefix - java version range */
        Map<String, Pair<String, String>> GROOVY_TO_JAVA_VERSIONS = [
                "2.": Pair.of(JAVA_8, JAVA_8),
                "3.": Pair.of(JAVA_8, JAVA_11),
                "4.": Pair.of(JAVA_8, JAVA_17)
        ]

        Pair<String, String> javaRange = null
        for (Map.Entry<String, Pair<String, String>> entry : GROOVY_TO_JAVA_VERSIONS.entrySet()) {
            if (groovyVersion.startsWith(entry.key)) {
                javaRange = entry.value
                break;
            }
        }

        return javaRange != null &&
                JavaEnvUtils.isAtLeastJavaVersion(javaRange.first) &&
                JavaEnvUtils.isAtMostJavaVersion(javaRange.second)
    }
}