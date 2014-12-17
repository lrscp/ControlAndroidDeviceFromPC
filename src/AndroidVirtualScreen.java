import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmuilib.ScreenShotDialog;
import com.android.ddmuilib.ScreenShotDialog.OnShowListener;

public class AndroidVirtualScreen {
    static ScreenShotDialog dialog;
    
    private static WelcomDialog welcomeDialog;

    public static void main(String[] args) {
        welcomeDialog = new WelcomDialog(new Shell(), SWT.DIALOG_TRIM);
        startScreenShotDialog();
        welcomeDialog.open();
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
                        if (dialog != null && !dialog.mShell.isDisposed()) {
                            dialog.mShell.dispose();
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
                                if (dialog == null) {
                                    dialog = new ScreenShotDialog(new Shell(Display.getDefault(), SWT.SHELL_TRIM));
                                    dialog.setOnShowListener(new OnShowListener() {
                                        @Override
                                        public void onShow() {
                                            if(welcomeDialog != null){
                                                welcomeDialog.getParent().dispose();
                                            }
                                        }
                                    });
                                    dialog.open(device);
                                    //AndroidDebugBridge.disconnectBridge();
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
