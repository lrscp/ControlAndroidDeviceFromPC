import lrscp.lib.CmdUtils.Service.OsCmd;
import lrscp.lib.swt.SwtUtils;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmuilib.ScreenShotDialog;
import com.android.ddmuilib.ScreenShotDialog.OnShowListener;

public class AndroidVirtualScreen {
    private static ScreenShotDialog mScreenShotDialog;

    private static WelcomDialog mWelcomeDialog;

    public static void main(String[] args) {
        if (!checkAdb()) {
            Shell shell = new Shell();
            SwtUtils.center(shell, 10);
            MessageDialog.openError(shell, "Error", "Adb is not installed in your system!");
            return;
        }
        mWelcomeDialog = new WelcomDialog(new Shell(), SWT.DIALOG_TRIM);
        startScreenShotDialog();
        mWelcomeDialog.open();
    }

    private static boolean checkAdb() {
        try {
            OsCmd.exec("adb");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void startScreenShotDialog() {
        AndroidDebugBridge.init(true);
        AndroidDebugBridge.createBridge("adb", true);
        AndroidDebugBridge.addDeviceChangeListener(new IDeviceChangeListener() {
            @Override
            public void deviceDisconnected(IDevice device) {
                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {
                        if (mScreenShotDialog != null && !mScreenShotDialog.mShell.isDisposed()) {
                            mScreenShotDialog.mShell.dispose();
                        }
                        System.exit(0);
                    }
                });
            }

            @Override
            public void deviceConnected(final IDevice device) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Display.getDefault().asyncExec(new Runnable() {
                            @Override
                            public void run() {
                                if (mScreenShotDialog == null) {
                                    mScreenShotDialog = new ScreenShotDialog(new Shell(Display.getDefault(), SWT.SHELL_TRIM));
                                    mScreenShotDialog.setOnShowListener(new OnShowListener() {
                                        @Override
                                        public void onShow() {
                                            if (mWelcomeDialog != null) {
                                                mWelcomeDialog.getParent().dispose();
                                            }
                                        }
                                    });
                                    mScreenShotDialog.open(device);
                                    // AndroidDebugBridge.disconnectBridge();
                                    System.exit(0);
                                }
                            }
                        });
                    }
                }).start();
            }

            @Override
            public void deviceChanged(IDevice device, int changeMask) {}
        });
    }
}
