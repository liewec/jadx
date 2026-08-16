package com.jadx.dexeditor.fragments;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.jadx.dexeditor.R;
import com.jadx.dexeditor.mcp.McpServer;
import com.jadx.dexeditor.mcp.McpService;

/**
 * MCP 服务页：启动/停止服务端、查看状态与日志（后台使用后可完整回看）、
 * 本地/局域网双地址一键复制、内置可整体选择复制的使用教程。
 */
public class McpFragment extends Fragment {

    private EditText portInput;
    private Button btnStart;
    private Button btnStop;
    private TextView statusText;
    private LinearLayout urlSection;
    private TextView urlLocalText;
    private TextView urlLanText;
    private TextView logText;
    private TextView tutorialText;
    private LinearLayout permRow;
    private TextView permText;
    private Button btnPerm;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuffer = new StringBuilder();
    private int pendingPort;

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    startServiceNow());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mcp, container, false);
        portInput = view.findViewById(R.id.mcp_port_input);
        btnStart = view.findViewById(R.id.btn_mcp_start);
        btnStop = view.findViewById(R.id.btn_mcp_stop);
        statusText = view.findViewById(R.id.mcp_status_text);
        urlSection = view.findViewById(R.id.mcp_url_section);
        urlLocalText = view.findViewById(R.id.mcp_url_local);
        urlLanText = view.findViewById(R.id.mcp_url_lan);
        logText = view.findViewById(R.id.mcp_log_text);
        tutorialText = view.findViewById(R.id.mcp_tutorial_text);
        permRow = view.findViewById(R.id.mcp_perm_row);
        permText = view.findViewById(R.id.mcp_perm_text);
        btnPerm = view.findViewById(R.id.btn_mcp_perm);
        logText.setMovementMethod(new ScrollingMovementMethod());

        btnPerm.setOnClickListener(v -> requestStoragePermission());
        btnStart.setOnClickListener(v -> onStartClicked());
        btnStop.setOnClickListener(v -> {
            McpService.stop(requireContext());
            // 稍后刷新状态
            mainHandler.postDelayed(this::refreshUi, 400);
        });
        view.findViewById(R.id.btn_mcp_copy_local).setOnClickListener(v ->
                copyText(urlLocalText.getText().toString(), R.string.mcp_copied_local));
        view.findViewById(R.id.btn_mcp_copy_lan).setOnClickListener(v ->
                copyText(urlLanText.getText().toString(), R.string.mcp_copied_lan));
        // 点击地址本身也可复制
        urlLocalText.setOnClickListener(v ->
                copyText(urlLocalText.getText().toString(), R.string.mcp_copied_local));
        urlLanText.setOnClickListener(v ->
                copyText(urlLanText.getText().toString(), R.string.mcp_copied_lan));

        buildTutorial();
        return view;
    }

    /** 整个教程合并为一段可选择复制的文本 */
    private void buildTutorial() {
        int[] parts = {R.string.mcp_tutorial_intro, R.string.mcp_tutorial_step1,
                R.string.mcp_tutorial_step2, R.string.mcp_tutorial_step3,
                R.string.mcp_tutorial_step4, R.string.mcp_tutorial_step5,
                R.string.mcp_tutorial_security};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(getString(parts[i]));
        }
        tutorialText.setText(sb);
    }

    private void onStartClicked() {
        pendingPort = parsePort();
        if (pendingPort <= 0) {
            Toast.makeText(requireContext(), R.string.mcp_port_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        // Android 13+ 通知权限（前台服务通知可见性；未授权服务仍可运行）
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startServiceNow();
        }
    }

    private void startServiceNow() {
        refreshPermRow();
        McpService.start(requireContext(), pendingPort);
        // 服务启动是异步的：延迟刷新几次状态（启动成功/失败日志由服务写入）
        mainHandler.postDelayed(this::refreshUi, 300);
        mainHandler.postDelayed(this::refreshUi, 900);
        mainHandler.postDelayed(this::refreshUi, 1800);
    }

    private int parsePort() {
        try {
            int p = Integer.parseInt(portInput.getText().toString().trim());
            return (p >= 1024 && p <= 65535) ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void copyText(String text, int toastRes) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("MCP URL", text));
            Toast.makeText(requireContext(), toastRes, Toast.LENGTH_SHORT).show();
        }
    }

    // ==== 文件访问权限（load_file / list_dir 按路径读取所需） ====

    private final ActivityResultLauncher<String[]> storagePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), res ->
                    refreshPermRow());

    /** 是否已具备按路径读取公共存储（Download 等）的能力 */
    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                // 跳转系统"所有文件访问"授权页，返回后 onResume 刷新状态
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + requireContext().getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            storagePermLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE});
        }
    }

    /** 未授权时显示提示行；onResume（从设置返回）与启动服务前都会刷新 */
    private void refreshPermRow() {
        boolean ok = hasStorageAccess();
        permRow.setVisibility(ok ? View.GONE : View.VISIBLE);
        if (!ok) {
            permText.setText(Build.VERSION.SDK_INT >= 30
                    ? R.string.mcp_perm_needed
                    : R.string.mcp_perm_needed_legacy);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPermRow();
        refreshUi();
        reloadLog();
        // 重新挂上日志监听（后台期间产生的日志由 reloadLog 从静态缓冲补齐）
        McpServer server = McpService.getRunningServer();
        if (server != null) {
            server.setLogListener(this::appendLog);
        }
    }

    @Override
    public void onPause() {
        McpServer server = McpService.getRunningServer();
        if (server != null) {
            server.setLogListener(null);
        }
        super.onPause();
    }

    private void refreshUi() {
        if (isDetached() || getContext() == null) return;
        boolean running = McpService.isRunning();
        btnStart.setEnabled(!running);
        btnStop.setEnabled(running);
        if (running) {
            McpServer server = McpService.getRunningServer();
            int port = server != null ? server.getPort() : McpServer.DEFAULT_PORT;
            String ip = McpServer.getLanIp();
            statusText.setText(getString(R.string.mcp_running, ip, port));
            // 本地地址：AI 软件与本应用在同一台手机时使用
            urlLocalText.setText(getString(R.string.mcp_url_local_value, port));
            // 局域网地址：AI 软件在电脑/其他设备时使用
            urlLanText.setText(getString(R.string.mcp_url_lan_value, ip, port));
            urlSection.setVisibility(View.VISIBLE);
            if (isResumed() && server != null) {
                server.setLogListener(this::appendLog);
            }
        } else {
            statusText.setText(R.string.mcp_stopped);
            urlSection.setVisibility(View.GONE);
        }
    }

    /** 从服务端静态缓冲重载日志（后台使用期间产生的日志不会丢失） */
    private void reloadLog() {
        String snapshot = McpServer.getLogSnapshot();
        mainHandler.post(() -> {
            logBuffer.setLength(0);
            logBuffer.append(snapshot);
            logText.setText(snapshot.isEmpty() ? getText(R.string.mcp_log_empty) : logBuffer);
        });
    }

    private void appendLog(String line) {
        mainHandler.post(() -> {
            logBuffer.append(line).append('\n');
            // 控制日志长度
            if (logBuffer.length() > 16000) {
                logBuffer.delete(0, logBuffer.length() - 12000);
            }
            logText.setText(logBuffer);
        });
    }
}
