package com.android.ddmlib;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShellCmdWrapper {
    static final String tag = "ShellCmdWrapper";

    static final Pattern resultCodePattern = Pattern.compile("return code is (\\d+)");

    IDevice mDevice = null;
    int returnCode = -1;

    public ShellCmdWrapper(IDevice device) {
        mDevice = device;
    }

    public int execShellCmdWithExitCode(String cmd, final StringBuilder response)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        try {
            mDevice.executeShellCommand(cmd + ";echo return code is $?", new MultiLineReceiver() {
                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public void processNewLines(String[] lines) {
                    for (String s : lines) {
                        Matcher matcher = resultCodePattern.matcher(s);
                        if (matcher.find()) {
                            returnCode = Integer.valueOf(matcher.group(1));
                        } else {
                            response.append(s);
                            response.append("\n");
                        }
                    }
                }
            });
        } catch (ShellCommandUnresponsiveException e) {
            Log.w(tag, e.getMessage());
        }

        return returnCode;
    }
}
