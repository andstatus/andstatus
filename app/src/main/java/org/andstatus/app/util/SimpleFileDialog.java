package org.andstatus.app.util;

/*
 * 
 * This file is licensed under The Code Project Open License (CPOL) 1.02 
 * http://www.codeproject.com/info/cpol10.aspx
 * http://www.codeproject.com/info/CPOL.zip
 * 
 * License Preamble:
 * This License governs Your use of the Work. This License is intended to allow developers to use the Source
 * Code and Executable Files provided as part of the Work in any application in any form.
 * 
 * The main points subject to the terms of the License are:
 *    Source Code and Executable Files can be used in commercial applications.
 *    Source Code and Executable Files can be redistributed, and
 *    Source Code can be modified to create derivative works.
 *    No claim of suitability, guarantee, or any warranty whatsoever is provided. The software is provided "as-is".
 *    The Article(s) accompanying the Work may not be distributed or republished without the Author's consent
 * 
 * This License is entered between You, the individual or other entity reading or otherwise making use of
 * the Work licensed pursuant to this License and the individual or other entity which offers the Work
 * under the terms of this License ("Author").
 *  (See Links above for full license text)
 */

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SimpleFileDialog {
    private static final int DARK_GREY_COLOR = 0xFF444444;

    private static final String ROOT_FOLDER = "/";
    
    public enum TypeOfSelection {
        FILE_OPEN,
        FILE_SAVE,
        FOLDER_CHOOSE
    }
    private TypeOfSelection typeOfSelection = TypeOfSelection.FILE_OPEN;
    
    private String mSdCardDirectory = "";
    private Context mContext;
    private TextView mTitleView1;
    private TextView mTitleView;
    private String mDefaultFileName = "";
    private String mSelectedFileName = mDefaultFileName;
    private EditText mInputText;

    private String mDir = "";
    private List<String> mSubdirs = null;
    private SimpleFileDialogListener mSimpleFileDialogListener = null;
    private ArrayAdapter<String> mListAdapter = null;

    /** Callback interface for selected directory */
    public interface SimpleFileDialogListener {
        public void onChosenDir(String chosenDir);
    }

    public SimpleFileDialog(Context context, TypeOfSelection typeOfSelectionIn,
            SimpleFileDialogListener listener) {
        mContext = context;
        if (typeOfSelectionIn != null) {
            typeOfSelection = typeOfSelectionIn;
        }
        mSdCardDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();
        mSimpleFileDialogListener = listener;

        try {
            mSdCardDirectory = new File(mSdCardDirectory).getCanonicalPath();
        } catch (IOException e) {
            MyLog.i("getCanonicalPath", e);
        }
    }

    /**
     * chooseFile_or_Dir() - load directory chooser dialog for initial default
     * sdcard directory
     */
    public void chooseFileOrDir() {
        if (StringUtils.isEmpty(mDir)) {
            chooseFileOrDir(mSdCardDirectory);
        } else {
            chooseFileOrDir(mDir);
        }
    }

    /**
     * chooseFile_or_Dir(String dir) - load directory chooser dialog for initial
     * input 'dir' directory
     */
    public void chooseFileOrDir(String dirIn) {
        mDir = fixInputDir(dirIn);
        if (StringUtils.isEmpty(mDir)) {
            return;
        }
        mSubdirs = getDirectories(mDir);

        AlertDialog.Builder dialogBuilder = createDirectoryChooserDialog(mDir, mSubdirs,
                new DirectoryChooserOnClickListener());

        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Current directory chosen
                // Call registered listener supplied with the chosen directory
                if (mSimpleFileDialogListener != null) {
                    if (typeOfSelection == TypeOfSelection.FILE_OPEN || typeOfSelection == TypeOfSelection.FILE_SAVE) {
                        mSelectedFileName = mInputText.getText() + "";
                        mSimpleFileDialogListener
                                .onChosenDir(mDir + "/" + mSelectedFileName);
                    } else {
                        mSimpleFileDialogListener.onChosenDir(mDir);
                    }
                }
            }
        }).setNegativeButton("Cancel", null);

        final AlertDialog dirsDialog = dialogBuilder.create();

        // Show directory chooser dialog
        dirsDialog.show();
    }

    private class DirectoryChooserOnClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int item) {
            String dirOld = mDir;
            String selectedName = "" + ((AlertDialog) dialog).getListView().getAdapter().getItem(item);
            if (selectedName.charAt(selectedName.length() - 1) == '/') {
                selectedName = selectedName.substring(0, selectedName.length() - 1);
            }

            // Navigate into the sub-directory
            if ("..".equals(selectedName)) {
                int slashInd = mDir.lastIndexOf("/");
                if (slashInd >= 0) {
                    mDir = mDir.substring(0, slashInd);
                    if (StringUtils.isEmpty(mDir)) {
                        mDir = getRootFolder();
                    }
                }
            } else {
                mDir += "/" + selectedName;
            }

            mSelectedFileName = mDefaultFileName;
            File dir = new File(mDir);
            if (dir.isFile()) {
                mDir = dirOld;
                mSelectedFileName = selectedName;
            } else {
                autoSelectFileInDirectory(dir);
            }
            updateDirectory();
        }

        private void autoSelectFileInDirectory(File dir) {
            File[] files = dir.listFiles();
            if (files != null ) {
                for (File file : files) {
                    if (file.isFile()) {
                        mSelectedFileName = file.getName();
                        break;
                    }
                }
            }
        }
    }
    
    private String fixInputDir(String dirIn) {
        String dir = dirIn;
        if (!isExistingDirectoryLogged(dir)) {
            dir = mSdCardDirectory;
            if (!isExistingDirectoryLogged(dir)) {
                dir = ROOT_FOLDER;
                if (!isExistingDirectoryLogged(dir)) {
                    Log.i("chooseFile_or_Dir", "No existing directories found");
                    return "";
                }
            }
        }
        try {
            dir = new File(dir).getCanonicalPath();
        } catch (IOException e) {
            MyLog.i("getCanonicalPath", e);
            return "";
        }
        return dir;
    }

    private boolean createSubDir(String newDir) {
        File newDirFile = new File(newDir);
        if (!newDirFile.exists()) {
            return newDirFile.mkdir();
        } else {
            return false;
        }
    }

    private List<String> getDirectories(String dir) {
        List<String> dirs = new ArrayList<String>();
        try {
            if (!isExistingDirectoryLogged(dir)) {
                dirs.add("..");
                return dirs;
            }
            File dirFile = new File(dir);
            File[] files = dirFile.listFiles();
            if (files == null) {
                Log.v("getDirectories", "Null listFiles: '" + dirFile.getAbsolutePath() + "'");
                dirs.add("..");
                return dirs;
            }

            // if directory is not the base sd card directory add ".." for going
            // up one directory
            if (!mDir.equals(mSdCardDirectory) && !ROOT_FOLDER.equals(mDir)) {
                dirs.add("..");
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    // Add "/" to directory names to identify them in the list
                    dirs.add(file.getName() + "/");
                } else if (typeOfSelection == TypeOfSelection.FILE_SAVE || typeOfSelection == TypeOfSelection.FILE_OPEN) {
                    // Add file names to the list if we are doing a file save or
                    // file open operation
                    dirs.add(file.getName());
                }
            }
        } catch (Exception e) {
            MyLog.i("GetDirectories", e);
        }

        Collections.sort(dirs, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });
        Log.v("Get directories", Arrays.toString(dirs.toArray()));
        return dirs;
    }

    private static boolean isExistingDirectoryLogged(String dir) {
        final String method = "isExistingDirectoryLogged";
        if (StringUtils.isEmpty(dir)) {
            Log.v(method, "Empty dir");
            return false;
        }
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            Log.v(method, "Does not exist:" + dirFile.getAbsolutePath());
            return false;
        }
        if (!dirFile.isDirectory()) {
            Log.v(method, "Not a directory:" + dirFile.getAbsolutePath());
            return false;
        }
        File[] files = dirFile.listFiles();
        if (files == null) {
            Log.v(method, "Null listFiles: '" + dirFile.getAbsolutePath() + "'");
            return false;
        }
        return true;
    }

    /** START DIALOG DEFINITION */
    private AlertDialog.Builder createDirectoryChooserDialog(String title, List<String> listItems,
            DialogInterface.OnClickListener onClickListener) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
        // Create title text showing file select type
        mTitleView1 = new TextView(mContext);
        mTitleView1.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        switch (typeOfSelection) {
            case FILE_OPEN:
                mTitleView1.setText("Open:");
                break;
            case FILE_SAVE:
                mTitleView1.setText("Save As:");
                break;
            case FOLDER_CHOOSE:
                mTitleView1.setText("Folder Select:");
                break;
            default:
                mTitleView1.setText("?? " + typeOfSelection);
                break;
        }

        // need to make this a variable Save as, Open, Select Directory
        mTitleView1.setGravity(Gravity.CENTER_VERTICAL);
        mTitleView1.setBackgroundColor(DARK_GREY_COLOR);
        mTitleView1.setTextColor(mContext.getResources().getColor(android.R.color.white));

        // Create custom view for AlertDialog title
        LinearLayout titleLayout1 = new LinearLayout(mContext);
        titleLayout1.setOrientation(LinearLayout.VERTICAL);
        titleLayout1.addView(mTitleView1);

        if (typeOfSelection == TypeOfSelection.FOLDER_CHOOSE || typeOfSelection == TypeOfSelection.FILE_SAVE) {
            // Create New Folder Button
            Button newDirButton = new Button(mContext);
            newDirButton.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            newDirButton.setText("New Folder");
            newDirButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final EditText input = new EditText(mContext);

                    // Show new folder name input dialog
                    new AlertDialog.Builder(mContext).
                            setTitle("New Folder Name").
                            setView(input)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    Editable newDir = input.getText();
                                    String newDirName = newDir.toString();
                                    // Create new directory
                                    if (createSubDir(mDir + "/" + newDirName)) {
                                        // Navigate into the new directory
                                        mDir += "/" + newDirName;
                                        updateDirectory();
                                    } else {
                                        Toast.makeText(mContext, "Failed to create '"
                                                + newDirName + "' folder", Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                }
                            }).setNegativeButton("Cancel", null).show();
                }
            }
                    );
            titleLayout1.addView(newDirButton);
        }

        // Create View with folder path and entry text box //
        LinearLayout titleLayout = new LinearLayout(mContext);
        titleLayout.setOrientation(LinearLayout.VERTICAL);

        mTitleView = new TextView(mContext);
        mTitleView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        mTitleView.setBackgroundColor(DARK_GREY_COLOR);
        mTitleView.setTextColor(mContext.getResources().getColor(android.R.color.white));
        mTitleView.setGravity(Gravity.CENTER_VERTICAL);
        mTitleView.setText(title);

        titleLayout.addView(mTitleView);

        if (typeOfSelection == TypeOfSelection.FILE_OPEN || typeOfSelection == TypeOfSelection.FILE_SAVE) {
            mInputText = new EditText(mContext);
            mInputText.setText(mDefaultFileName);
            titleLayout.addView(mInputText);
        }

        // Set Views and Finish Dialog builder
        dialogBuilder.setView(titleLayout);
        dialogBuilder.setCustomTitle(titleLayout1);
        mListAdapter = createListAdapter(listItems);
        dialogBuilder.setSingleChoiceItems(mListAdapter, -1, onClickListener);
        dialogBuilder.setCancelable(false);
        return dialogBuilder;
    }

    private void updateDirectory() {
        mSubdirs.clear();
        mSubdirs.addAll(getDirectories(mDir));
        mTitleView.setText(mDir);
        mListAdapter.notifyDataSetChanged();
        // #scorch
        if (typeOfSelection == TypeOfSelection.FILE_SAVE || typeOfSelection == TypeOfSelection.FILE_OPEN) {
            mInputText.setText(mSelectedFileName);
        }
    }

    private ArrayAdapter<String> createListAdapter(List<String> items) {
        return new ArrayAdapter<String>(mContext, android.R.layout.select_dialog_item,
                android.R.id.text1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    // Enable list item (directory) text wrapping
                    TextView tv = (TextView) v;
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }

    public static String getRootFolder() {
        return ROOT_FOLDER;
    }
}
