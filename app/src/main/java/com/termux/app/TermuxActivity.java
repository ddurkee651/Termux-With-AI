package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.ai.AIAssistantChatAdapter;
import com.termux.app.ai.AIAssistantMessage;
import com.termux.app.ai.GeminiApi;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    interface DeltaCallback {
        void onDelta(String s);
    }

    TermuxService mTermuxService;
    TerminalView mTerminalView;
    TermuxTerminalViewClient mTermuxTerminalViewClient;
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    private TermuxAppSharedPreferences mPreferences;
    private TermuxAppSharedProperties mProperties;
    TermuxActivityRootView mTermuxActivityRootView;
    View mTermuxActivityBottomSpaceView;
    ExtraKeysView mExtraKeysView;
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;
    TermuxSessionsListViewController mTermuxSessionListViewController;
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();
    Toast mLastToast;
    private boolean mIsVisible;
    private boolean mIsOnResumeAfterOnCreate = false;
    private boolean mIsActivityRecreated = false;
    private boolean mIsInvalidState;
    private int mNavBarHeight;
    private float mTerminalToolbarDefaultHeight;

    private AIAssistantChatAdapter mAIAssistantChatAdapter;
    private final ArrayList<AIAssistantMessage.Attachment> mPendingAttachments = new ArrayList<>();

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD_ID = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    public static Intent newInstance(Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static void startTermuxActivity(Context context) {
        if (context == null) return;

        try {
            context.startActivity(newInstance(context));
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start TermuxActivity", e);
        }
    }

    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        Intent intent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intent.setPackage(TermuxConstants.TERMUX_PACKAGE_NAME);
        intent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(intent);
    }


    private static final String AI_PREFS = "ai_assistant_prefs";
    private static final String P_MODEL_SOURCE = "model_source";
    private static final String P_LOCAL_MODEL_PATH = "local_model_path";
    private static final String P_HF_REPO = "hf_repo";
    private static final String P_HF_FILE = "hf_file";
    private static final String P_HF_TOKEN = "hf_token";
    private static final String P_GEMINI_KEY = "gemini_key";
    private static final String P_GEMINI_MODEL = "gemini_model";

    private static final int REQ_PICK_LOCAL_MODEL = 2001;
    private static final int REQ_PICK_ATTACH_FILES = 2002;

    private static final String LLAMA_ASSET_DIR = "llama/arm64-v8a";
    private static final String[] LLAMA_ASSET_FILES = new String[]{
        "llama-cli",
        "libllama.so",
        "libllama.so.0",
        "libllama.so.0.0.1",
        "libmtmd.so",
        "libmtmd.so.0",
        "libmtmd.so.0.0.1",
        "libggml.so",
        "libggml.so.0",
        "libggml.so.0.9.5",
        "libggml-base.so",
        "libggml-base.so.0",
        "libggml-base.so.0.9.5",
        "libggml-cpu.so",
        "libggml-cpu.so.0",
        "libggml-cpu.so.0.9.5",
        "libssl.so.3",
        "libcrypto.so.3",
        "libc++_shared.so"
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        setAIAssistantDrawerView();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);
        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;
        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return;
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException ignored) {
                    }
                });
            } else {
                finishActivityIfNotFinishing();
            }
        } else {
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");
        finishActivityIfNotFinishing();
    }

    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }

    private void setActivityTheme() {
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    private void setTermuxTerminalViewAndClients() {
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class)));
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_PICK_LOCAL_MODEL && data != null && data.getData() != null) {
                Uri uri = data.getData();
                new Thread(() -> {
                    try {
                        String path = copyUriToInternalModels(uri);
                        aiPrefs().edit().putString(P_LOCAL_MODEL_PATH, path).apply();
                        runOnUiThread(() -> {
                            android.widget.TextView tv = findViewById(R.id.txt_local_model_path);
                            if (tv != null) tv.setText(path);
                            Toast.makeText(this, "Model set", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Model import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
                return;
            }

            if (requestCode == REQ_PICK_ATTACH_FILES && data != null) {
                if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    for (int i = 0; i < cd.getItemCount(); i++) {
                        Uri uri = cd.getItemAt(i).getUri();
                        if (uri != null) mPendingAttachments.add(readAttachmentMeta(uri));
                    }
                } else if (data.getData() != null) {
                    Uri uri = data.getData();
                    mPendingAttachments.add(readAttachmentMeta(uri));
                }
                if (!mPendingAttachments.isEmpty())
                    Toast.makeText(this, "Attached: " + mPendingAttachments.size(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) requestStoragePermission(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: " + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) requestStoragePermission(true);
    }

    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD_ID, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD_ID:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        if (!TermuxActivity.this.isFinishing()) finish();
    }

    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }

    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }

    public View getRightDrawer() {
        return findViewById(R.id.right_drawer);
    }

    public void openRightDrawer() {
        DrawerLayout drawerLayout = getDrawer();
        View rightDrawer = getRightDrawer();
        if (drawerLayout != null && rightDrawer != null) drawerLayout.openDrawer(rightDrawer);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public void toggleTerminalToolbar() {
        ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        if (terminalToolbarViewPager.getVisibility() == View.VISIBLE) {
            terminalToolbarViewPager.setVisibility(View.GONE);
            mPreferences.setShowTerminalToolbar(false);
        } else {
            terminalToolbarViewPager.setVisibility(View.VISIBLE);
            mPreferences.setShowTerminalToolbar(true);
        }
    }

    public boolean isTerminalViewSelected() {
        ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        return terminalToolbarViewPager == null || terminalToolbarViewPager.getCurrentItem() == 0;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null) return mTerminalView.getCurrentSession();
        return null;
    }

    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController != null) mTermuxSessionListViewController.notifyDataSetChanged();
    }

    public void reloadActivityStyling() {
        setMargins();
        setTerminalToolbarHeight();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        if (intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, false))
                            recreate();
                        else
                            reloadActivityStyling();
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private SharedPreferences aiPrefs() {
        return getSharedPreferences(AI_PREFS, MODE_PRIVATE);
    }

    private File modelsDir() {
        File d = new File(getFilesDir(), "models");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private String resolveDisplayName(Uri uri) {
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) {
                    String n = c.getString(idx);
                    c.close();
                    return n;
                }
                c.close();
            }
        } catch (Exception ignored) {
        }
        return "model.gguf";
    }

    private String copyUriToInternalModels(Uri uri) throws Exception {
        String name = resolveDisplayName(uri);
        if (name == null || name.trim().isEmpty()) name = "model.gguf";
        File out = new File(modelsDir(), name);
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Could not open input stream");
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[1024 * 1024];
        int r;
        while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
        fos.flush();
        fos.close();
        in.close();
        return out.getAbsolutePath();
    }

    private void ensureBundledLlamaReady() throws Exception {
        File dir = new File(getFilesDir(), LLAMA_ASSET_DIR);
        if (!dir.exists()) dir.mkdirs();

        AssetManager am = getAssets();
        for (String name : LLAMA_ASSET_FILES) {
            String assetPath = LLAMA_ASSET_DIR + "/" + name;
            File out = new File(dir, name);

            boolean need = !out.exists() || out.length() == 0;
            if (!need) continue;

            InputStream in = am.open(assetPath, AssetManager.ACCESS_STREAMING);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[1024 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
            fos.flush();
            fos.close();
            in.close();

            out.setReadable(true, true);
            out.setWritable(true, true);
            out.setExecutable(true, true);
        }

        File cli = new File(dir, "llama-cli");
        if (!cli.exists()) throw new Exception("Bundled llama-cli missing");
    }

    private void downloadHf(String repo, String filename, String token, File outFile) throws Exception {
        String url = "https://huggingface.co/" + repo + "/resolve/main/" + filename;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        if (token != null && !token.trim().isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token.trim());

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

        InputStream in = new BufferedInputStream(conn.getInputStream());
        FileOutputStream fos = new FileOutputStream(outFile);
        byte[] buf = new byte[1024 * 1024];
        int r;
        while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
        fos.flush();
        fos.close();
        in.close();
        conn.disconnect();
    }

    private AIAssistantMessage.Attachment readAttachmentMeta(Uri uri) {
        String name = "";
        String mime = "";
        long size = -1;

        try {
            ContentResolver cr = getContentResolver();
            mime = cr.getType(uri);

            Cursor c = cr.query(uri, null, null, null, null);
            if (c != null) {
                int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = c.getColumnIndex(OpenableColumns.SIZE);
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) name = c.getString(nameIdx);
                    if (sizeIdx >= 0) size = c.getLong(sizeIdx);
                }
                c.close();
            }
        } catch (Exception ignored) {
        }

        return new AIAssistantMessage.Attachment(uri, name, mime, size);
    }

    private void setAIAssistantDrawerView() {
        MaterialButtonToggleGroup toggle = findViewById(R.id.model_source_toggle);

        LinearLayout localControls = findViewById(R.id.local_controls);
        LinearLayout hfControls = findViewById(R.id.hf_controls);
        LinearLayout cloudControls = findViewById(R.id.cloud_controls);

        MaterialButton pickLocal = findViewById(R.id.btn_pick_local_model);
        android.widget.TextView localPathText = findViewById(R.id.txt_local_model_path);

        EditText hfRepo = findViewById(R.id.edt_hf_repo);
        EditText hfFile = findViewById(R.id.edt_hf_filename);
        EditText hfToken = findViewById(R.id.edt_hf_token);
        MaterialButton hfDownload = findViewById(R.id.btn_download_hf);
        android.widget.TextView hfStatus = findViewById(R.id.txt_hf_status);

        EditText gemKey = findViewById(R.id.edt_gemini_api_key);
        EditText gemModel = findViewById(R.id.edt_gemini_model);
        MaterialButton gemSave = findViewById(R.id.btn_save_cloud);
        android.widget.TextView gemStatus = findViewById(R.id.txt_cloud_status);

        RecyclerView chat = findViewById(R.id.ai_chat_list);
        View send = findViewById(R.id.btn_send);
        EditText input = findViewById(R.id.edt_ai_message);
        View attach = findViewById(R.id.btn_attach_file);

        if (toggle == null) return;
        if (localControls == null || hfControls == null || cloudControls == null) return;
        if (pickLocal == null || localPathText == null) return;
        if (hfRepo == null || hfFile == null || hfToken == null || hfDownload == null || hfStatus == null) return;
        if (gemKey == null || gemModel == null || gemSave == null || gemStatus == null) return;
        if (chat == null || send == null || input == null || attach == null) return;

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        chat.setLayoutManager(lm);

        mAIAssistantChatAdapter = new AIAssistantChatAdapter();
        chat.setAdapter(mAIAssistantChatAdapter);

        SharedPreferences p = aiPrefs();

        String savedLocalPath = p.getString(P_LOCAL_MODEL_PATH, "");
        localPathText.setText(savedLocalPath == null || savedLocalPath.isEmpty() ? "No model selected" : savedLocalPath);

        hfRepo.setText(p.getString(P_HF_REPO, ""));
        hfFile.setText(p.getString(P_HF_FILE, ""));
        hfToken.setText(p.getString(P_HF_TOKEN, ""));
        gemKey.setText(p.getString(P_GEMINI_KEY, ""));
        gemModel.setText(p.getString(P_GEMINI_MODEL, "gemini-3-flash-preview"));

        int src = p.getInt(P_MODEL_SOURCE, 0);
        if (src == 0) toggle.check(R.id.model_source_local);
        else if (src == 1) toggle.check(R.id.model_source_hf);
        else toggle.check(R.id.model_source_cloud);

        Runnable refreshPanels = () -> {
            int checked = toggle.getCheckedButtonId();
            localControls.setVisibility(checked == R.id.model_source_local ? View.VISIBLE : View.GONE);
            hfControls.setVisibility(checked == R.id.model_source_hf ? View.VISIBLE : View.GONE);
            cloudControls.setVisibility(checked == R.id.model_source_cloud ? View.VISIBLE : View.GONE);

            int v = checked == R.id.model_source_local ? 0 : (checked == R.id.model_source_hf ? 1 : 2);
            p.edit().putInt(P_MODEL_SOURCE, v).apply();
        };

        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            refreshPanels.run();
        });
        refreshPanels.run();

        attach.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, REQ_PICK_ATTACH_FILES);
        });

        pickLocal.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_PICK_LOCAL_MODEL);
        });

        hfDownload.setOnClickListener(v -> {
            String repo = hfRepo.getText() == null ? "" : hfRepo.getText().toString().trim();
            String file = hfFile.getText() == null ? "" : hfFile.getText().toString().trim();
            String token = hfToken.getText() == null ? "" : hfToken.getText().toString().trim();

            p.edit().putString(P_HF_REPO, repo).putString(P_HF_FILE, file).putString(P_HF_TOKEN, token).apply();

            if (repo.isEmpty() || file.isEmpty()) {
                hfStatus.setText("Repo and filename required");
                return;
            }

            hfStatus.setText("Downloading…");

            new Thread(() -> {
                try {
                    File out = new File(modelsDir(), file);
                    downloadHf(repo, file, token, out);
                    String path = out.getAbsolutePath();
                    p.edit().putString(P_LOCAL_MODEL_PATH, path).apply();
                    runOnUiThread(() -> {
                        hfStatus.setText("Downloaded: " + path);
                        localPathText.setText(path);
                        toggle.check(R.id.model_source_local);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> hfStatus.setText("Download failed: " + e.getMessage()));
                }
            }).start();
        });

        gemSave.setOnClickListener(v -> {
            String key = gemKey.getText() == null ? "" : gemKey.getText().toString().trim();
            String model = gemModel.getText() == null ? "" : gemModel.getText().toString().trim();
            if (model.isEmpty()) model = "gemini-3-flash-preview";
            p.edit().putString(P_GEMINI_KEY, key).putString(P_GEMINI_MODEL, model).apply();
            gemStatus.setText("Saved");
        });

        send.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString();
            if (text.trim().isEmpty() && mPendingAttachments.isEmpty()) return;

            int checked = toggle.getCheckedButtonId();
            int chosen = checked == R.id.model_source_local ? 0 : (checked == R.id.model_source_hf ? 1 : 2);

            AIAssistantMessage.ModelSource srcModelSource =
                chosen == 0 ? AIAssistantMessage.ModelSource.LOCAL :
                    (chosen == 1 ? AIAssistantMessage.ModelSource.HUGGINGFACE : AIAssistantMessage.ModelSource.GEMINI);

            String modelId = srcModelSource == AIAssistantMessage.ModelSource.GEMINI
                ? p.getString(P_GEMINI_MODEL, "gemini-3-flash-preview")
                : "";

            long ts = System.currentTimeMillis();
            long idUser = ts;
            long idAssistant = ts + 1;

            List<AIAssistantMessage.Attachment> atts = mPendingAttachments.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(mPendingAttachments);

            AIAssistantMessage userMsg = AIAssistantMessage.user(idUser, ts, text, atts, srcModelSource, modelId == null ? "" : modelId);
            mAIAssistantChatAdapter.addMessage(userMsg);

            input.setText("");
            mPendingAttachments.clear();
            chat.post(() -> chat.scrollToPosition(mAIAssistantChatAdapter.getItemCount() - 1));

            AIAssistantMessage typing = AIAssistantMessage.assistant(idAssistant, System.currentTimeMillis(), "", AIAssistantMessage.Status.STREAMING, srcModelSource, modelId == null ? "" : modelId);
            mAIAssistantChatAdapter.addMessage(typing);
            chat.post(() -> chat.scrollToPosition(mAIAssistantChatAdapter.getItemCount() - 1));

            final long assistantId = idAssistant;
            final String prompt = text;

            new Thread(() -> {
                try {
                    if (srcModelSource == AIAssistantMessage.ModelSource.GEMINI) {
                        String key = p.getString(P_GEMINI_KEY, "");
                        String m = p.getString(P_GEMINI_MODEL, "gemini-3-flash-preview");
                        if (key == null || key.trim().isEmpty()) throw new Exception("Gemini API key missing");

                        StringBuilder live = new StringBuilder();
                        GeminiApi.Result r = GeminiApi.streamGenerateContentSse(
                            key.trim(),
                            m == null ? "gemini-3-flash-preview" : m,
                            prompt,
                            null,
                            delta -> {
                                live.append(delta);
                                AIAssistantMessage upd = AIAssistantMessage.assistant(
                                    assistantId,
                                    System.currentTimeMillis(),
                                    live.toString(),
                                    AIAssistantMessage.Status.STREAMING,
                                    AIAssistantMessage.ModelSource.GEMINI,
                                    m == null ? "gemini-3-flash-preview" : m
                                );
                                runOnUiThread(() -> {
                                    mAIAssistantChatAdapter.updateMessage(assistantId, upd);
                                    chat.post(() -> chat.scrollToPosition(mAIAssistantChatAdapter.getItemCount() - 1));
                                });
                            }
                        );

                        String finalText = r.text;
                        if (r.functionCall != null) finalText = finalText + "\n\n[FunctionCall]\n" + r.functionCall.toString();

                        AIAssistantMessage finalMsg = AIAssistantMessage.assistant(
                            assistantId,
                            System.currentTimeMillis(),
                            finalText,
                            AIAssistantMessage.Status.FINAL,
                            AIAssistantMessage.ModelSource.GEMINI,
                            m == null ? "gemini-3-flash-preview" : m
                        );

                        runOnUiThread(() -> {
                            mAIAssistantChatAdapter.updateMessage(assistantId, finalMsg);
                            chat.post(() -> chat.scrollToPosition(mAIAssistantChatAdapter.getItemCount() - 1));
                        });

                    } else {
                        String path = p.getString(P_LOCAL_MODEL_PATH, "");
                        if (path == null || path.trim().isEmpty()) throw new Exception("Local model not set");

                        StringBuilder live = new StringBuilder();
                        bundledLlamaGenerateStreamingBytes(path.trim(), prompt, delta -> {
                            live.append(delta);
                            AIAssistantMessage upd = AIAssistantMessage.assistant(
                                assistantId,
                                System.currentTimeMillis(),
                                live.toString(),
                                AIAssistantMessage.Status.STREAMING,
                                srcModelSource,
                                ""
                            );
                            runOnUiThread(() -> {
                                mAIAssistantChatAdapter.updateMessage(assistantId, upd);
                                chat.post(() -> chat.scrollToPosition(mAIAssistantChatAdapter.getItemCount() - 1));
                            });
                        });

                        AIAssistantMessage finalMsg = AIAssistantMessage.assistant(
                            assistantId,
                            System.currentTimeMillis(),
                            live.toString().trim(),
                            AIAssistantMessage.Status.FINAL,
                            srcModelSource,
                            ""
                        );

                        runOnUiThread(() -> {
                            mAIAssistantChatAdapter.updateMessage(assistantId, finalMsg);
                            chat.post(() -> chat.scrollToPosition(mAIAssistantChatAdapter.getItemCount() - 1));
                        });
                    }

                } catch (Exception e) {
                    AIAssistantMessage err = AIAssistantMessage.error(
                        assistantId,
                        System.currentTimeMillis(),
                        e.getMessage(),
                        srcModelSource,
                        modelId == null ? "" : modelId
                    );
                    runOnUiThread(() -> {
                        mAIAssistantChatAdapter.updateMessage(assistantId, err);
                        chat.post(() -> chat.scrollToPosition(mAIAssistantChatAdapter.getItemCount() - 1));
                    });
                }
            }).start();
        });
    }

    private void bundledLlamaGenerateStreamingBytes(String modelPath, String prompt, DeltaCallback onDelta) throws Exception {
        ensureBundledLlamaReady();

        File runDir = new File(getFilesDir(), LLAMA_ASSET_DIR);
        File cli = new File(runDir, "llama-cli");
        if (!cli.exists()) throw new Exception("llama-cli missing after extract");

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(cli.getAbsolutePath());
        cmd.add("-m");
        cmd.add(modelPath);
        cmd.add("-p");
        cmd.add(prompt);
        cmd.add("-n");
        cmd.add("512");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(runDir);
        pb.redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH", runDir.getAbsolutePath());

        Process pr = pb.start();
        InputStream is = pr.getInputStream();

        String needle = "> " + prompt;
        StringBuilder line = new StringBuilder();
        boolean inAnswer = false;
        boolean done = false;

        StringBuilder pending = new StringBuilder();
        long lastFlush = 0L;

        byte[] buf = new byte[4096];
        int n;

        while (!done && (n = is.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                char c = (char) (buf[i] & 0xFF);
                if (c == '\r') continue;

                if (c == '\n') {
                    String ln = line.toString();
                    line.setLength(0);

                    if (!inAnswer) {
                        if (ln.trim().equals(needle.trim())) inAnswer = true;
                        continue;
                    }

                    if (ln.startsWith(">")) {
                        done = true;
                        break;
                    }
                    if (ln.startsWith("[ Prompt:")) continue;
                    if (ln.trim().equals("Exiting...")) {
                        done = true;
                        break;
                    }

                    pending.append(ln).append('\n');

                } else {
                    line.append(c);
                }
            }

            long now = System.currentTimeMillis();
            if (pending.length() > 0 && (now - lastFlush) > 80) {
                if (onDelta != null) onDelta.onDelta(pending.toString());
                pending.setLength(0);
                lastFlush = now;
            }
        }

        if (pending.length() > 0 && onDelta != null) onDelta.onDelta(pending.toString());

        is.close();
        int exit = pr.waitFor();
        if (exit != 0) throw new Exception("llama-cli exit code: " + exit);
    }
}
