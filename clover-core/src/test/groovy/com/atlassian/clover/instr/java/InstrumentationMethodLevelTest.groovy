package com.atlassian.clover.instr.java

import com.atlassian.clover.cfg.instr.InstrumentationConfig
import com.atlassian.clover.cfg.instr.InstrumentationLevel
import com.atlassian.clover.cfg.instr.java.JavaInstrumentationConfig
import org.junit.Test

class InstrumentationMethodLevelTest extends InstrumentationTestBase {

    @Test
    void testMethodLevelInstr() throws Exception {
        checkStatement("int i = 0;", "int i = 0;", InstrumentationLevel.METHOD.ordinal())
        checkStatement("int i = arg == 2 ? 3 : 4;", "int i = arg == 2 ? 3 : 4;", InstrumentationLevel.METHOD.ordinal())
        checkStatement("assert arg > 0;", "assert arg > 0;", InstrumentationLevel.METHOD.ordinal())

        JavaInstrumentationConfig config = getInstrConfig(newDbTempFile().getAbsolutePath(), false, true, false)
        config.setFlushPolicy(InstrumentationConfig.INTERVAL_FLUSHING)
        config.setInstrLevel(InstrumentationLevel.METHOD.ordinal())
        checkInstrumentation([
                ["class B { public B(int arg) {}}",
                 "class B {" + snifferField + " public B(int arg) {try{RECORDER.inc(0);}finally{RECORDER.maybeFlush();}}}"]
        ] as String[][],
                config)

        config = getInstrConfig(newDbTempFile().getAbsolutePath(), false, true, false)
        config.setFlushPolicy(InstrumentationConfig.INTERVAL_FLUSHING)
        config.setInstrLevel(InstrumentationLevel.METHOD.ordinal())

        checkInstrumentation([
                ["class B { public B(int arg) {int i = 0;}}",
                 "class B {" + snifferField + " public B(int arg) {try{RECORDER.inc(0);int i = 0;}finally{RECORDER.maybeFlush();}}}"]
        ] as String[][],
                config)

        config = getInstrConfig(newDbTempFile().getAbsolutePath(), false, true, false)
        config.setFlushPolicy(InstrumentationConfig.THREADED_FLUSHING)
        config.setInstrLevel(InstrumentationLevel.METHOD.ordinal())

        checkInstrumentation([
                ["class B { public B(int arg) {}}",
                 "class B {" + snifferField + " public B(int arg) {try{RECORDER.inc(0);}finally{RECORDER.flushNeeded();}}}"]
        ] as String[][],
                config)

        config = getInstrConfig(newDbTempFile().getAbsolutePath(), false, true, false)
        config.setFlushPolicy(InstrumentationConfig.THREADED_FLUSHING)
        config.setInstrLevel(InstrumentationLevel.METHOD.ordinal())

        checkInstrumentation([
                ["class B { public B(int arg) {int i = 0;}}",
                 "class B {" + snifferField + " public B(int arg) {try{RECORDER.inc(0);int i = 0;}finally{RECORDER.flushNeeded();}}}"]
        ] as String[][],
                config)
    }
}
