/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.settingslib;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.system.Os;
import android.system.StructUtsname;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceInfoUtils {
    private static final String TAG = "DeviceInfoUtils";

    private static final String FILENAME_MSV = "/sys/board_properties/soc/msv";
    private static final String FILENAME_PROC_VERSION = "/proc/version";

    /**
     * Reads a line from the specified file.
     * @param filename the file to read from
     * @return the first line, if any.
     * @throws IOException if the file couldn't be read
     */
    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    public static String getFormattedKernelVersion(Context context) {
        String procVersion = null;
        try {
            procVersion = readLine(FILENAME_PROC_VERSION);
        } catch (IOException e) {
            Log.w(TAG, "Unable to read " + FILENAME_PROC_VERSION, e);
        }
        return formatKernelVersion(context, Os.uname(), procVersion);
    }

    @VisibleForTesting
    static String formatKernelVersion(Context context, StructUtsname uname,
            @Nullable String procVersion) {
        final String version = formatKernelVersion(context, uname);
        final String compiler = formatKernelCompiler(procVersion);
        if (compiler == null
                || version.equals(context.getString(R.string.status_unavailable))) {
            return version;
        }
        return version + "\n" + compiler;
    }

    /**
     * Returns the compiler and linker the kernel was built with, taken from /proc/version,
     * for example "Clang 21.0.0 (r563880c), LLD 21.0.0", or null if neither is found.
     */
    @VisibleForTesting
    @Nullable
    static String formatKernelCompiler(@Nullable String procVersion) {
        if (TextUtils.isEmpty(procVersion)) {
            return null;
        }
        String compiler = null;
        Matcher m = Pattern.compile("clang version (\\d+(?:\\.\\d+)*)").matcher(procVersion);
        if (m.find()) {
            compiler = "Clang " + m.group(1);
            final Matcher r = Pattern.compile("based on (r\\d+[a-z]*)").matcher(procVersion);
            if (r.find()) {
                compiler += " (" + r.group(1) + ")";
            }
        } else {
            m = Pattern.compile("gcc (?:version )?(?:\\([^)]*\\) )?(\\d+(?:\\.\\d+)+)")
                    .matcher(procVersion);
            if (m.find()) {
                compiler = "GCC " + m.group(1);
            }
        }
        String linker = null;
        m = Pattern.compile("LLD (\\d+(?:\\.\\d+)*)").matcher(procVersion);
        if (m.find()) {
            linker = "LLD " + m.group(1);
        } else {
            m = Pattern.compile("GNU ld (?:\\([^)]*\\) )?(\\d+(?:\\.\\d+)+)")
                    .matcher(procVersion);
            if (m.find()) {
                linker = "GNU ld " + m.group(1);
            }
        }
        if (compiler == null) {
            return linker;
        }
        return linker == null ? compiler : compiler + ", " + linker;
    }

    @VisibleForTesting
    static String formatKernelVersion(Context context, StructUtsname uname) {
        if (uname == null) {
            return context.getString(R.string.status_unavailable);
        }
        // Example:
        // 4.9.29-g958411d
        // #1 SMP PREEMPT Wed Jun 7 00:06:03 CST 2017
        final String VERSION_REGEX =
                "(#\\d+) " +              /* group 1: "#1" */
                "(?:.*?)?" +              /* ignore: optional SMP, PREEMPT, and any CONFIG_FLAGS */
                "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)"; /* group 2: "Thu Jun 28 11:02:39 PDT 2012" */
        Matcher m = Pattern.compile(VERSION_REGEX).matcher(uname.version);
        if (!m.matches()) {
            Log.e(TAG, "Regex did not match on uname version " + uname.version);
            return context.getString(R.string.status_unavailable);
        }

        // Example output:
        // 4.9.29-g958411d
        // #1 Wed Jun 7 00:06:03 CST 2017
        return new StringBuilder().append(uname.release)
                .append("\n")
                .append(m.group(1))
                .append(" ")
                .append(m.group(2)).toString();
    }

    /**
     * Returns " (ENGINEERING)" if the msv file has a zero value, else returns "".
     * @return a string to append to the model number description.
     */
    public static String getMsvSuffix() {
        // Production devices should have a non-zero value. If we can't read it, assume it's a
        // production device so that we don't accidentally show that it's an ENGINEERING device.
        try {
            String msv = readLine(FILENAME_MSV);
            // Parse as a hex number. If it evaluates to a zero, then it's an engineering build.
            if (Long.parseLong(msv, 16) == 0) {
                return " (ENGINEERING)";
            }
        } catch (IOException|NumberFormatException e) {
            // Fail quietly, as the file may not exist on some devices, or may be unreadable
        }
        return "";
    }

    public static String getFeedbackReporterPackage(Context context) {
        final String feedbackReporter =
                context.getResources().getString(R.string.oem_preferred_feedback_reporter);
        if (TextUtils.isEmpty(feedbackReporter)) {
            // Reporter not configured. Return.
            return feedbackReporter;
        }
        // Additional checks to ensure the reporter is on system image, and reporter is
        // configured to listen to the intent. Otherwise, dont show the "send feedback" option.
        final Intent intent = new Intent(Intent.ACTION_BUG_REPORT);

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolvedPackages =
                pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);
        for (ResolveInfo info : resolvedPackages) {
            if (info.activityInfo != null) {
                if (!TextUtils.isEmpty(info.activityInfo.packageName)) {
                    try {
                        ApplicationInfo ai =
                                pm.getApplicationInfo(info.activityInfo.packageName, 0);
                        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                            // Package is on the system image
                            if (TextUtils.equals(
                                    info.activityInfo.packageName, feedbackReporter)) {
                                return feedbackReporter;
                            }
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        // No need to do anything here.
                    }
                }
            }
        }
        return null;
    }

    /** Returns security patch in default locale. */
    public static @Nullable String getSecurityPatch() {
        return getSecurityPatch(Locale.getDefault());
    }

    /** Returns security patch in given locale. */
    public static @Nullable String getSecurityPatch(@NonNull Locale locale) {
        String patch = Build.VERSION.SECURITY_PATCH;
        if (!"".equals(patch)) {
            try {
                SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd");
                Date patchDate = template.parse(patch);
                String format = DateFormat.getBestDateTimePattern(locale, "dMMMMyyyy");
                patch = DateFormat.format(format, patchDate).toString();
            } catch (ParseException e) {
                // broken parse; fall through and use the raw string
            }
            return patch;
        } else {
            return null;
        }
    }

    /**
     * Format a phone number.
     * @param subscriptionInfo {@link SubscriptionInfo} subscription information.
     * @return Returns formatted phone number.
     */
    public static String getFormattedPhoneNumber(Context context,
            SubscriptionInfo subscriptionInfo) {
        String formattedNumber = null;
        if (subscriptionInfo != null) {
            final TelephonyManager telephonyManager = context.getSystemService(
                    TelephonyManager.class);
            final String rawNumber = telephonyManager.createForSubscriptionId(
                    subscriptionInfo.getSubscriptionId()).getLine1Number();
            if (!TextUtils.isEmpty(rawNumber)) {
                formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
            }
        }
        return formattedNumber;
    }

    public static String getFormattedPhoneNumbers(Context context,
            List<SubscriptionInfo> subscriptionInfoList) {
        StringBuilder sb = new StringBuilder();
        if (subscriptionInfoList != null) {
            final TelephonyManager telephonyManager = context.getSystemService(
                    TelephonyManager.class);
            final int count = subscriptionInfoList.size();
            for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                final String rawNumber = telephonyManager.createForSubscriptionId(
                        subscriptionInfo.getSubscriptionId()).getLine1Number();
                if (!TextUtils.isEmpty(rawNumber)) {
                    sb.append(PhoneNumberUtils.formatNumber(rawNumber)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * To get the formatting text for display in a potentially opposite-directionality context
     * without garbling.
     * @param subscriptionInfo {@link SubscriptionInfo} subscription information.
     * @return Returns phone number with Bidi format.
     */
    public static String getBidiFormattedPhoneNumber(Context context,
            SubscriptionInfo subscriptionInfo) {
        final String phoneNumber = getFormattedPhoneNumber(context, subscriptionInfo);
        return BidiFormatter.getInstance().unicodeWrap(phoneNumber, TextDirectionHeuristics.LTR);
    }
}
