//   Copyright 2009 Google Inc.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package com.navjagpal.fileshare;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

public class FileShare extends Activity {

  private static final String TAG = "FileSharer";

  /* startActivity request codes */
  private static final int PICK_FILE_REQUEST = 1;
  private static final int PICK_FOLDER_REQUEST = 2;

  /* Used to keep track of the currently selected file */
  private Uri mFileToShare;

  private static final int DIALOG_PASSWORD = 0;

  private final View.OnClickListener mAddFileListener = new View.OnClickListener() {
    public void onClick(View v) {
      Intent pickFileIntent = new Intent();
      pickFileIntent.setAction(Intent.ACTION_GET_CONTENT);
      pickFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
      pickFileIntent.setType("*/*");
      Intent chooserIntent = Intent.createChooser(pickFileIntent,
          getText(R.string.choosefile_title));
      startActivityForResult(chooserIntent, PICK_FILE_REQUEST);
    }
  };

  private final View.OnClickListener mManageContentListener = new View.OnClickListener() {
    public void onClick(View v) {
      Intent manageIntent = new Intent();
      manageIntent.setAction(Intent.ACTION_MAIN);
      manageIntent.setType(FileSharingProvider.Folders.CONTENT_TYPE);
      startActivity(manageIntent);
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    /* Startup the FileSharingService, unless the last state was off. */
    final SharedPreferences sharedPreferences = getSharedPreferences(
            FileSharingService.PREFS_NAME, MODE_PRIVATE);
    ToggleButton serviceButton = (ToggleButton) findViewById(R.id.service);
    if (sharedPreferences.getBoolean(
    		FileSharingService.PREFS_SERVICE_ON_STARTUP, true)) {  
	    Intent serviceIntent = new Intent();
	    serviceIntent.setAction("com.navjagpal.filesharer.IFileSharingService");
	    startService(serviceIntent);
	    serviceButton.setChecked(true);
    } else {
    	serviceButton.setChecked(false);
    }
    
    /* Setup toggling the service. */
    serviceButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean newValue) {
			Intent serviceIntent = new Intent();
		    serviceIntent.setAction("com.navjagpal.filesharer.IFileSharingService");
			if (newValue == true) {
			    startService(serviceIntent);
			} else {
				stopService(serviceIntent);
			}
		    SharedPreferences.Editor editor = sharedPreferences.edit();
		    editor.putBoolean(FileSharingService.PREFS_SERVICE_ON_STARTUP, newValue);
		    editor.commit();
		}
    });

    /* Add the add file to shared folder */
    Button addFileButton = (Button) findViewById(R.id.addfile);
    addFileButton.setOnClickListener(mAddFileListener);

    /* Manage content button */
    Button manageButton = (Button) findViewById(R.id.manage);
    manageButton.setOnClickListener(mManageContentListener);

    /* Setup the status text */
    TextView ipTextView = (TextView) findViewById(R.id.url);
    ipTextView.setText("http://" + getIPAddress(this) + ":9999");

    /* Preferences button */
    Button preferencesButton = (Button) findViewById(R.id.preferences);
    preferencesButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(FileShare.this);

        builder.setTitle("Preferences");
        builder.setMultiChoiceItems(new CharSequence[] { "Allow Uploads",
        "Require Password" }, new boolean[] {
            sharedPreferences.getBoolean(
                FileSharingService.PREFS_ALLOW_UPLOADS, false),
                sharedPreferences.getBoolean(
                    FileSharingService.PREFS_REQUIRE_LOGIN, false) },
                    new DialogInterface.OnMultiChoiceClickListener() {
          public void onClick(DialogInterface dialog, 
              int item, boolean value) {
            if (item == 0) {
              SharedPreferences.Editor editor = sharedPreferences.edit();
              editor.putBoolean(FileSharingService.PREFS_ALLOW_UPLOADS,
                  value);
              editor.commit();
            } else if (item == 1) {
              SharedPreferences.Editor editor = sharedPreferences.edit();
              editor.putBoolean(FileSharingService.PREFS_REQUIRE_LOGIN,
                  value);
              editor.commit();
              /* If user has enabled the password feature, 
               * show the password dialog. */
              if (value == true) {
                showDialog(DIALOG_PASSWORD);
              }
            }
          }
        });
        builder.setPositiveButton("Done", null);
        builder.show();
      }
    });

    /* Help button */
    Button helpButton = (Button) findViewById(R.id.help);
    helpButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Intent helpIntent = new Intent();
        helpIntent.setClass(FileShare.this, Help.class);
        startActivity(helpIntent);
      }
    });

    /* Display a "What's New" dialog if necessary. */
    PackageManager pm = getPackageManager();
    try {
      PackageInfo pi = pm.getPackageInfo("com.navjagpal.fileshare", 0);
      if (!sharedPreferences.getBoolean(pi.versionName, false)) {
        sharedPreferences.edit().putBoolean(pi.versionName, true).commit();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.whats_new_title);
        builder.setMessage(R.string.whats_new);
        builder.setPositiveButton("OK", null);
        builder.show();
      }
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode != RESULT_OK)
      return;

    switch (requestCode) {
      case PICK_FILE_REQUEST:
        /* Store this file somewhere */
        mFileToShare = data.getData();

        /* Now pick a folder */
        Intent pickFolder = new Intent();
        pickFolder.setAction(Intent.ACTION_PICK);
        pickFolder.setType(FileSharingProvider.Folders.CONTENT_ITEM_TYPE);
        startActivityForResult(pickFolder, PICK_FOLDER_REQUEST);
        break;
      case PICK_FOLDER_REQUEST:
        addFileToFolder(mFileToShare, data.getData());
        break;

    }
  }

  /**
   * Adds a file to a shared folder. If the provided file is actually a 
   * folder, all files under that folder will be added to the shared folder.
   * This includes files from sub-directories as well.
   * 
   * @param file Uri for file.
   * @param folder Uri for shared folder.
   */
  private void addFileToFolder(Uri file, Uri folder) {
    /* The URI could be a folder. If it is, assume we want all files under that
     * folder.
     */
    if (getContentResolver().getType(file).equals(
        FileProvider.CONTENT_TYPE)) {
      Cursor c = managedQuery(
          file, new String[] {OpenableColumns.DISPLAY_NAME},
          null, null, null);
      while (c.moveToNext()) {
        String filename = c.getString(c.getColumnIndex(
            OpenableColumns.DISPLAY_NAME));
        Uri uri = Uri.withAppendedPath(
            file, filename);
        addFileToFolder(uri, folder);
      }
    } else {
      try {
        FileSharingProvider.addFileToFolder(getContentResolver(), file, folder);
      } catch (SQLException exception) {
        Log.w(TAG, "Error adding file " + file + " to folder " + folder);
      }
    }
  }

  public static String getIPAddress(Context context) {
    WifiManager wifiManager = (WifiManager) context
    .getSystemService(Context.WIFI_SERVICE);
    android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);

    byte[] intByte;
    try {
      dos.writeInt(info.getIpAddress());
      dos.flush();
      intByte = bos.toByteArray();
    } catch (IOException e) {
      Log.e(TAG, "Problem converting IP address");
      return "unknown";
    }

    // Reverse int bytes.. damn, this is a hack.
    byte[] addressBytes = new byte[intByte.length];
    for (int i = 0; i < intByte.length; i++) {
      addressBytes[i] = intByte[(intByte.length - 1) - i];
    }

    InetAddress address = null;
    try {
      address = InetAddress.getByAddress(addressBytes);
    } catch (UnknownHostException e) {
      Log.e(TAG, "Problem determing IP address");
      return "unknown";
    }
    return address.getHostAddress();
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog;
    switch (id) {
      case DIALOG_PASSWORD:
        dialog = new Dialog(FileShare.this);
        dialog.setContentView(R.layout.password_dialog);
        dialog.setTitle(R.string.set_password_title);
        final EditText passwordText = (EditText) dialog
        .findViewById(R.id.password);
        passwordText.setText(getSharedPreferences(FileSharingService.PREFS_NAME,
            MODE_PRIVATE).getString(FileSharingService.PREFS_PASSWORD, ""));
        Button okButton = (Button) dialog.findViewById(R.id.ok_button);
        final Dialog passwordDialog = dialog;
        okButton.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) {
            SharedPreferences preferences = getSharedPreferences(
                FileSharingService.PREFS_NAME, MODE_PRIVATE);
            String newPassword = passwordText.getText().toString();
            if (!newPassword.equals(preferences.getString(
                FileSharingService.PREFS_PASSWORD, ""))) {
              SharedPreferences.Editor editor = preferences.edit();
              editor.putString(FileSharingService.PREFS_PASSWORD, passwordText
                  .getText().toString());
              editor.commit();
              /* The password has changed, delete all cookies. */
              new CookiesDatabaseOpenHelper(FileShare.this).getWritableDatabase()
              .delete("cookies", null, null);
            }
            passwordDialog.dismiss();
          }
        });
        break;
      default:
        dialog = null;
    }
    return dialog;
  }

}