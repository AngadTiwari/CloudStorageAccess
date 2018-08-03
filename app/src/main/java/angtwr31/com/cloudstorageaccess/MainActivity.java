package angtwr31.com.cloudstorageaccess;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;


import org.mortbay.jetty.Main;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int COMPLETE_AUTHORIZATION_REQUEST_CODE = 2001;
    private static final int DIALOG_ACCOUNTCHOSER = 3001;
    private static String TAG = MainActivity.class.getSimpleName();
    // In the class declaration section:
    private static final int RC_SIGN_IN = 1001;
    private DropboxAPI<AndroidAuthSession> mDBApi;
    private GoogleSignInClient mGoogleSignInClient;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;
    private Drive service;

    private AccountManager accountManager;
    private Account[] accounts;
    private String authName;
    // https://developers.google.com/drive/scopes
    private static final String AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/drive";

    // https://code.google.com/apis/console/
    private static final String CLIENT_ID = "788481420661-v4ol3hms8vmhamuvv04r5k1mkm6jvd74.apps.googleusercontent.com";
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dropboxLogin();
        googleDriveLogin();
        //googleDriveVer3Login();
        loginViaAccountManager();
    }

    private void loginViaAccountManager() {
        accountManager = AccountManager.get(this);
        accounts = accountManager.getAccountsByType("com.google");

        if (accounts == null || accounts.length == 0) {
            // TODO
        } else if (accounts.length == 1) {
            processAccountSelected(accounts[0]);
        } else if (accounts.length > 1) {
            showDialog(DIALOG_ACCOUNTCHOSER);
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (id) {
            case DIALOG_ACCOUNTCHOSER:
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

                String[] names = new String[accounts.length];

                for (int i = 0; i < accounts.length; i++) {
                    names[i] = accounts[i].name;
                }

                alertDialogBuilder.setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                alertDialogBuilder.setTitle("Select a Google account");
                return alertDialogBuilder.create();
        }

        return null;
    }

    private void processAccountSelected(Account account) {
        if (account != null) {
            authName = account.name.toString();
            Toast.makeText(this, authName, Toast.LENGTH_LONG).show();

            accountManager.getAuthToken(account, AUTH_TOKEN_TYPE, null, MainActivity.this,
                    new AccountManagerCallback<Bundle>() {

                        public void run(final AccountManagerFuture<Bundle> future) {
                            try {
                                authToken = future.getResult().getString(AccountManager.KEY_AUTHTOKEN);
                                processTokenReceived();
                            } catch (OperationCanceledException exception) {
                                // TODO
                            } catch (Exception exception) {
                                Log.d(this.getClass().getName(), exception.getMessage());
                            }
                        }
                    }, null);
        }
    }
    private void processTokenReceived() {
        final HttpTransport transport = AndroidHttp.newCompatibleTransport();
        final JsonFactory jsonFactory = new GsonFactory();
        GoogleCredential credential = new GoogleCredential();
        credential.setAccessToken(authToken);

        Drive drive = new Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName(getString(R.string.app_name))
                .build();

        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    About about = drive.about().get().setFields("user, storageQuota").execute();
                    long quota = about.getStorageQuota().getLimit();
                    Log.d("quota", "" + quota);
                } catch (IOException iex) {
                    iex.printStackTrace();
                }
            }
        });
    }

    private void googleDriveVer3Login() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Drive service = DriveQuickstart.getGoogleDriveService(MainActivity.this);
                    About about = service.about().get().execute();
                    long quota = about.getStorageQuota().getLimit();
                    Log.d("quota", "" + quota);
                } catch (GeneralSecurityException gex){
                    gex.printStackTrace();
                } catch (IOException iex) {
                    iex.printStackTrace();
                }
            }
        });
    }

    private void googleDriveJavaClientLogin(GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE));
        credential.setSelectedAccountName("angtwr31@gmail.com");//account.getDisplayName());
        service = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).build();

        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    About about = service.about().get().execute();
                    long quota = about.getStorageQuota().getLimit();
                    Log.d("quota", ""+quota);
                } catch (UserRecoverableAuthIOException ex) {
                    startActivityForResult(ex.getIntent(), COMPLETE_AUTHORIZATION_REQUEST_CODE);
                } catch (IOException e) {
                    System.out.println("An error occurred: " + e);
                }
            }
        });
    }

    private void googleDriveLogin() {
        mGoogleSignInClient = buildGoogleSignInClient();
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private GoogleSignInClient buildGoogleSignInClient() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(com.google.android.gms.drive.Drive.SCOPE_FILE)
                        .build();
        return GoogleSignIn.getClient(this, signInOptions);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case COMPLETE_AUTHORIZATION_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    Executors.newSingleThreadExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // App is authorized, you can go back to sending the API request
                                About about = service.about().get().execute();
                                long quota = about.getStorageQuota().getLimit();
                                Log.d("quota", ""+quota);
                            } catch (IOException iex) {
                                iex.printStackTrace();
                            }
                        }
                    });
                } else {
                    // User denied access, show him the account chooser again
                }
                break;

            case RC_SIGN_IN:
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                handleSignInResult(task);
                break;
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            mDriveClient = com.google.android.gms.drive.Drive.getDriveClient(getApplicationContext(), account);
            mDriveResourceClient = com.google.android.gms.drive.Drive.getDriveResourceClient(getApplicationContext(), account);
            //googleDriveJavaClientLogin(account);
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
        }
    }

    private void dropboxLogin() {
        // Create Dropbox client
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
        DbxClientV2 client = new DbxClientV2(config, DropboxAccess.ACCESS_TOKEN);
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long usedspace = client.users().getSpaceUsage().getUsed();
                    Log.d("quota", "" + usedspace);
                } catch (DbxException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}
