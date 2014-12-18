import lrscp.lib.swt.SwtUtils;

import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;


public class WelcomDialog extends Dialog {

    protected Object result;
    protected Shell shlLoading;

    /**
     * Create the dialog.
     * @param parent
     * @param style
     */
    public WelcomDialog(Shell parent, int style) {
        super(parent, style);
        setText("SWT Dialog");
    }

    /**
     * Open the dialog.
     * @return the result
     */
    public Object open() {
        createContents();
        shlLoading.open();
        shlLoading.layout();
        Display display = getParent().getDisplay();
        while (!shlLoading.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return result;
    }

    /**
     * Create contents of the dialog.
     */
    private void createContents() {
        shlLoading = new Shell(getParent(), getStyle());
        shlLoading.setSize(274, 157);
        shlLoading.setText("Loading");
        GridLayout gl_shlLoading = new GridLayout(1, false);
        gl_shlLoading.marginWidth = 20;
        gl_shlLoading.marginHeight = 20;
        shlLoading.setLayout(gl_shlLoading);
        
        ProgressBar progressBar = new ProgressBar(shlLoading, SWT.BORDER | SWT.SMOOTH | SWT.INDETERMINATE);
        GridData gd_progressBar = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_progressBar.heightHint = 39;
        progressBar.setLayoutData(gd_progressBar);
        
        Label lblLoading = new Label(shlLoading, SWT.NONE);
        GridData gd_lblLoading = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1);
        gd_lblLoading.verticalIndent = 10;
        lblLoading.setLayoutData(gd_lblLoading);
        lblLoading.setText("Connecting andorid device...");

        shlLoading.pack();
        SwtUtils.center(shlLoading, 10);
    }

}
