/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.system.StructUtsname;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DeviceInfoUtilsTest {

    private Context mContext;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
    }

    @Test
    public void formatKernelVersion_regularInputVersion_shouldStripOptionalValues() {
        final String sysName = "Linux";
        final String nodeName = "localhost";
        final String release = "4.4.88-g134be430baab";
        final String version = "#1 SMP PREEMPT Tue Dec 31 12:00:00 UTC 2017";
        final String machine = "aarch64";
        final StructUtsname uname = new StructUtsname(sysName, nodeName, release, version, machine);

        final String expected = release + "\n" + "#1 Tue Dec 31 12:00:00 UTC 2017";

        assertThat(DeviceInfoUtils.formatKernelVersion(mContext, uname)).isEqualTo(expected);
    }

    @Test
    public void formatKernelVersion_nonRegularInputVersion_shouldBeUnavailable() {
        final String sysName = "Linux";
        final String nodeName = "localhost";
        final String release = "4.4.88-g134be430baab";
        final String version = "%@%!asd%#@!$" + "\n " + "fasdfasdfa13ta";
        final String machine = "aarch64";
        final StructUtsname uname = new StructUtsname(sysName, nodeName, release, version, machine);

        final String expected = mContext.getString(R.string.status_unavailable);

        assertThat(DeviceInfoUtils.formatKernelVersion(mContext, uname)).isEqualTo(expected);
    }

    @Test
    public void formatKernelVersion_nullInputVersion_shouldBeUnavailable() {
        final String expected = mContext.getString(R.string.status_unavailable);

        assertThat(DeviceInfoUtils.formatKernelVersion(mContext, null)).isEqualTo(expected);
    }

    private static final String PROC_VERSION_CLANG = "Linux version 6.1.182-android14-11 "
            + "(delano@axion) (Android (14054515, +pgo, +bolt, +lto, +mlgo, based on r563880c) "
            + "clang version 21.0.0 (https://android.googlesource.com/toolchain/llvm-project "
            + "5e96669f0607), LLD 21.0.0 (/out/llvm-project/llvm 5e96669f0607)) "
            + "#1 SMP PREEMPT Mon Nov 23 12:15:33 UTC 2026";

    @Test
    public void formatKernelVersion_withProcVersion_shouldAddCompiler() {
        final String release = "6.1.182-android14-11";
        final StructUtsname uname = new StructUtsname("Linux", "localhost", release,
                "#1 SMP PREEMPT Mon Nov 23 12:15:33 UTC 2026", "aarch64");

        final String expected = release + "\n" + "#1 Mon Nov 23 12:15:33 UTC 2026"
                + "\n" + "Clang 21.0.0 (r563880c), LLD 21.0.0";

        assertThat(DeviceInfoUtils.formatKernelVersion(mContext, uname, PROC_VERSION_CLANG))
                .isEqualTo(expected);
    }

    @Test
    public void formatKernelVersion_unavailableWithProcVersion_shouldBeUnavailable() {
        final String expected = mContext.getString(R.string.status_unavailable);

        assertThat(DeviceInfoUtils.formatKernelVersion(mContext, null, PROC_VERSION_CLANG))
                .isEqualTo(expected);
    }

    @Test
    public void formatKernelCompiler_gcc_shouldReturnGccAndLd() {
        final String procVersion = "Linux version 5.4.0 (u@h) (gcc (GCC) 13.2.0, "
                + "GNU ld (GNU Binutils) 2.41) #1 SMP Mon Jan 1 00:00:00 UTC 2024";

        assertThat(DeviceInfoUtils.formatKernelCompiler(procVersion))
                .isEqualTo("GCC 13.2.0, GNU ld 2.41");
    }

    @Test
    public void formatKernelCompiler_unknownOrMissing_shouldBeNull() {
        assertThat(DeviceInfoUtils.formatKernelCompiler("Linux version 6.1 (u@h) #1")).isNull();
        assertThat(DeviceInfoUtils.formatKernelCompiler(null)).isNull();
    }
}
