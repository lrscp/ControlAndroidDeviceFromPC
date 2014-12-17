/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ddmuilib;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import lrscp.lib.swt.SwtUtils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.ImageTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.android.chimpchat.core.IChimpDevice;
import com.android.chimpchat.core.TouchPressType;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;

/**
 * Gather a screen shot from the device and save it to a file.
 */
public class ScreenShotDialog extends Dialog implements DisposeListener, Listener {
    public static final float MAX_WIDTH = 1000;
    public static final float MAX_HEIGHT = 700;

    private Label mBusyLabel;
    private Label mImageLabel;

    private Button mSave;
    private Button mCopy;
    private Button mRotate;
    private Button mRefresh;
    private Button mRepeat;

    private IDevice mDevice;
    private IChimpDevice mChimDevice;
    private RawImage mRawImage;
    private Clipboard mClipboard;

    public Display mDisplay = null;
    public Shell mShell = null;
    
    private boolean isMouseDown = false;
    /** Number of 90 degree rotations applied to the current image */
    private int mRotateCount = 0;

    public float mImageScaleRatio = 1;
    private Image mCurImage = null;
    private int charIdx = 0;
    ContinualCapture captureThread = null;
    
    private OnShowListener mLsnr;

    public static interface OnShowListener{
        void onShow();
    }
    
    /**
     * Create with default style.
     */
    public ScreenShotDialog(Shell parent) {
        this(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        mClipboard = new Clipboard(parent.getDisplay());
    }

    /**
     * Create with app-defined style.
     */
    public ScreenShotDialog(Shell parent, int style) {
        super(parent, style);
    }

    public void setOnShowListener(OnShowListener lsnr){
        mLsnr = lsnr;
    }
    
    /**
     * Prepare and display the dialog.
     * 
     * @param device
     *            The {@link IDevice} from which to get the screenshot.
     */
    public void open(IDevice device) {
        mDevice = device;
        mChimDevice = device.getChimpDevice();

        Shell parent = getParent();
        mShell = new Shell(parent, getStyle());
        mShell.setText("Device Screen Capture");
        mDisplay = parent.getDisplay();

        createContents(mShell);
        mShell.pack();
        mShell.open();
        SwtUtils.center(mShell, mShell.getBounds().height + 150);

        notifyOnShow();

        updateDeviceImage(mShell);
        
        while (!mShell.isDisposed()) {
            if (!mDisplay.readAndDispatch())
                mDisplay.sleep();
        }
    }

    private void notifyOnShow() {
        if(mLsnr != null){
            mLsnr.onShow();
        }
    }

    /*
     * Create the screen capture dialog contents.
     */
    private void createContents(final Shell shell) {
        GridData data;

        final int colCount = 5;

        shell.setLayout(new GridLayout(colCount, true));
        // Add listeners
        shell.addDisposeListener(this);
        mDisplay.addFilter(SWT.KeyDown, this);
        mDisplay.addFilter(SWT.KeyUp, this);

        // "refresh" button
        mRefresh = new Button(shell, SWT.PUSH);
        mRefresh.setText("Refresh");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        mRefresh.setLayoutData(data);
        mRefresh.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateDeviceImage(shell);
                // RawImage only allows us to rotate the image 90 degrees at the
                // time,
                // so to preserve the current rotation we must call getRotated()
                // the same number of times the user has done it manually.
                // TODO: improve the RawImage class.
                for (int i = 0; i < mRotateCount; i++) {
                    mRawImage = mRawImage.getRotated();
                }
                updateImageDisplay(shell);
            }
        });

        // "rotate" button
        mRotate = new Button(shell, SWT.PUSH);
        mRotate.setText("Rotate");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        mRotate.setLayoutData(data);
        mRotate.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (mRawImage != null) {
                    mRotateCount = (mRotateCount + 1) % 4;
                    mRawImage = mRawImage.getRotated();
                    updateImageDisplay(shell);
                }
            }
        });

        // "save" button
        mSave = new Button(shell, SWT.PUSH);
        mSave.setText("Save");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        mSave.setLayoutData(data);
        mSave.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                saveImage(shell);
            }
        });

        mCopy = new Button(shell, SWT.PUSH);
        mCopy.setText("Copy");
        mCopy.setToolTipText("Copy the screenshot to the clipboard");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        mCopy.setLayoutData(data);
        mCopy.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                copy();
            }
        });

        // "Repeat start" button
        mRepeat = new Button(shell, SWT.PUSH);
        mRepeat.setText("Repeat start");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        mRepeat.setLayoutData(data);
        mRepeat.addSelectionListener(new SelectionAdapter() {
            boolean enabled = false;

            @Override
            public void widgetSelected(SelectionEvent e) {
                if (enabled) {
                    enabled = false;
                    mRefresh.setEnabled(true);
                    mCopy.setEnabled(true);
                    mSave.setEnabled(true);
                    mRotate.setEnabled(true);
                    if (captureThread != null) {
                        captureThread.exit();
                    }
                } else {
                    enabled = true;
                    mRefresh.setEnabled(false);
                    mCopy.setEnabled(false);
                    mSave.setEnabled(false);
                    mRotate.setEnabled(false);
                    captureThread = new ContinualCapture(shell);
                    captureThread.start();
                }
            }
        });

        // title/"capturing" label
        mBusyLabel = new Label(shell, SWT.NONE);
        mBusyLabel.setText("Preparing...");
        data = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
        data.horizontalSpan = colCount;
        mBusyLabel.setLayoutData(data);

        // space for the image
        mImageLabel = new Label(shell, SWT.BORDER);
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.horizontalSpan = colCount;
        mImageLabel.setLayoutData(data);
        Display display = shell.getDisplay();
        mImageLabel.setImage(ImageLoader.createPlaceHolderArt(display, 50, 50,
                display.getSystemColor(SWT.COLOR_BLUE)));
        mImageLabel.addMouseListener(new MouseListener() {
            @Override
            public void mouseUp(MouseEvent e) {
                isMouseDown = false;
                mChimDevice.touch((int) (e.x * mImageScaleRatio), (int) (e.y * mImageScaleRatio),
                        TouchPressType.UP);
            }

            @Override
            public void mouseDown(MouseEvent e) {
                isMouseDown = true;
                mChimDevice.touch((int) (e.x * mImageScaleRatio), (int) (e.y * mImageScaleRatio),
                        TouchPressType.DOWN);
            }

            @Override
            public void mouseDoubleClick(MouseEvent e) {}
        });
        mImageLabel.addMouseMoveListener(new MouseMoveListener() {
            @Override
            public void mouseMove(MouseEvent e) {
                if (isMouseDown) {
                    try {
                        mChimDevice.getManager().touchMove((int) (e.x * mImageScaleRatio),
                                (int) (e.y * mImageScaleRatio));
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        // shell.setDefaultButton(mRepeat);
    }

    /**
     * Copies the content of {@link #mImageLabel} to the clipboard.
     */
    private void copy() {
        mClipboard.setContents(new Object[] { mImageLabel.getImage().getImageData() },
                new Transfer[] { ImageTransfer.getInstance() });
    }

    final class ContinualCapture extends Thread implements ShellListener {
        private boolean mExit = false;
        private Shell mShell;

        public ContinualCapture(Shell shell) {
            mShell = shell;
            shell.addShellListener(this);
        }

        public void exit() {
            mExit = true;
            try {
                this.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(null, "ContinualCapture exits");
        }

        @Override
        public void run() {
            while (!mExit) {
                mRawImage = getDeviceImage();
                // convert raw data to an Image.
                if (mRawImage == null) {
                    continue;
                }
                PaletteData palette = new PaletteData(mRawImage.getRedMask(),
                        mRawImage.getGreenMask(), mRawImage.getBlueMask());

                ImageData imageData = new ImageData(mRawImage.width, mRawImage.height,
                        mRawImage.bpp, palette, 1, mRawImage.data);
                if (mCurImage != null) {
                    synchronized (mCurImage) {
                        try {
                            mCurImage.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                mImageScaleRatio = Math.max(1,
                        Math.max(mRawImage.width / MAX_WIDTH, mRawImage.height / MAX_HEIGHT));
                mCurImage = new Image(mShell.getDisplay(), imageData.scaledTo(
                        (int) (mRawImage.width / mImageScaleRatio),
                        (int) (mRawImage.height / mImageScaleRatio)));

                mShell.getDisplay().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        if (mShell.isDisposed()) {
                            return;
                        }
                        mBusyLabel.setText("refresh".substring(0, charIdx++ % 8));
                        mImageLabel.setImage(mCurImage);
                        mImageLabel.pack();
                        mShell.pack();
                        // there's no way to restore old cursor; assume it's
                        // ARROW
                        mShell.setCursor(mShell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
                        synchronized (mCurImage) {
                            mCurImage.notifyAll();
                            mCurImage = null;
                        }
                    }
                });
            }
        }

        @Override
        public void shellActivated(ShellEvent e) {}

        @Override
        public void shellClosed(ShellEvent e) {
            exit();
        }

        @Override
        public void shellDeactivated(ShellEvent e) {}

        @Override
        public void shellDeiconified(ShellEvent e) {}

        @Override
        public void shellIconified(ShellEvent e) {}
    }

    /**
     * Captures a new image from the device, and display it.
     */
    private void updateDeviceImage(Shell shell) {
        mBusyLabel.setText("Capturing..."); // no effect

        shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

        mRawImage = getDeviceImage();

        updateImageDisplay(shell);
    }

    /**
     * Updates the display with {@link #mRawImage}.
     * 
     * @param shell
     */
    private void updateImageDisplay(Shell shell) {
        Image image;
        if (mRawImage == null) {
            Display display = shell.getDisplay();
            image = ImageLoader.createPlaceHolderArt(display, 320, 240,
                    display.getSystemColor(SWT.COLOR_BLUE));

            mSave.setEnabled(false);
            mBusyLabel.setText("Screen not available");
        } else {
            // convert raw data to an Image.
            PaletteData palette = new PaletteData(mRawImage.getRedMask(), mRawImage.getGreenMask(),
                    mRawImage.getBlueMask());

            mImageScaleRatio = Math.max(1,
                    Math.max(mRawImage.width / MAX_WIDTH, mRawImage.height / MAX_HEIGHT));
            ImageData imageData = new ImageData(mRawImage.width, mRawImage.height, mRawImage.bpp,
                    palette, 1, mRawImage.data);
            image = new Image(getParent().getDisplay(), imageData.scaledTo(
                    (int) (mRawImage.width / mImageScaleRatio),
                    (int) (mRawImage.height / mImageScaleRatio)));

            mSave.setEnabled(true);
            mBusyLabel.setText("Captured image:");
        }

        mImageLabel.setImage(image);
        mImageLabel.pack();
        shell.pack();

        // there's no way to restore old cursor; assume it's ARROW
        shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
    }

    /**
     * Grabs an image from an ADB-connected device and returns it as a
     * {@link RawImage}.
     */
    private RawImage getDeviceImage() {
        try {
            return mDevice.getScreenshot();
        } catch (IOException ioe) {
            Log.w("ddms", "Unable to get frame buffer: " + ioe.getMessage());
            return null;
        } catch (TimeoutException e) {
            Log.w("ddms", "Unable to get frame buffer: timeout ");
            return null;
        } catch (AdbCommandRejectedException e) {
            Log.w("ddms", "Unable to get frame buffer: " + e.getMessage());
            return null;
        }
    }

    /*
     * Prompt the user to save the image to disk.
     */
    private void saveImage(Shell shell) {
        FileDialog dlg = new FileDialog(shell, SWT.SAVE);

        Calendar now = Calendar.getInstance();
        String fileName = String.format("device-%tF-%tH%tM%tS.png", now, now, now, now);

        dlg.setText("Save image...");
        dlg.setFileName(fileName);

        String lastDir = DdmUiPreferences.getStore().getString("lastImageSaveDir");
        if (lastDir.length() == 0) {
            lastDir = DdmUiPreferences.getStore().getString("imageSaveDir");
        }
        dlg.setFilterPath(lastDir);
        dlg.setFilterNames(new String[] { "PNG Files (*.png)" });
        dlg.setFilterExtensions(new String[] { "*.png" //$NON-NLS-1$
        });

        fileName = dlg.open();
        if (fileName != null) {
            // FileDialog.getFilterPath() does NOT always return the current
            // directory of the FileDialog; on the Mac it sometimes just returns
            // the value the dialog was initialized with. It does however return
            // the full path as its return value, so just pick the path from
            // there.
            if (!fileName.endsWith(".png")) {
                fileName = fileName + ".png";
            }

            String saveDir = new File(fileName).getParent();
            if (saveDir != null) {
                DdmUiPreferences.getStore().setValue("lastImageSaveDir", saveDir);
            }

            Log.d("ddms", "Saving image to " + fileName);
            ImageData imageData = mImageLabel.getImage().getImageData();

            try {
                org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader();

                loader.data = new ImageData[] { imageData };
                loader.save(fileName, SWT.IMAGE_PNG);
            } catch (SWTException e) {
                Log.w("ddms", "Unable to save " + fileName + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void widgetDisposed(DisposeEvent e) {
        if (captureThread != null) {
            captureThread.exit();
        }
        mDevice.releaseChimpDevice();
        mDisplay.removeFilter(SWT.KeyDown, this);
        mDisplay.removeFilter(SWT.KeyUp, this);
    }

    int lastKeyDown = -1;

    @Override
    public void handleEvent(Event event) {
        // Log.d(null, "key:" + event.keyCode + " " + event.type);
        if (event.type == SWT.KeyDown || event.type == SWT.KeyUp) {
            if (mChimDevice != null) {
                TouchPressType type = event.type == SWT.KeyDown ? TouchPressType.DOWN
                        : TouchPressType.UP;
                //Don't send repeat key downs
                if(event.type == SWT.KeyDown){
                    if (lastKeyDown == event.keyCode){
                        return;
                    } else {
                        lastKeyDown = event.keyCode;
                    }
                } else{
                    lastKeyDown = -1;
                }
                
                mChimDevice.press(PcKeyMap.getDeviceKey(event.keyCode), type);
                event.type = SWT.NONE;
            }
        }
    }
}
