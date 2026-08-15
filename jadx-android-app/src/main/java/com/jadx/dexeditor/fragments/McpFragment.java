package com.jadx.dexeditor.fragments;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
 * MCP 服务页：启动/停止服务端、查看状态与日志、内置使用教程。
 */
public class McpFragment extends Fragment {

    private EditText portInput;
    private Button btnStart;
    private Button btnStop;
    private TextView statusText;
    private TextView urlText;
    private TextView logText;

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
        urlText = view.findViewById(R.id.mcp_url_text);
        logText = view.findViewById(R.id.mcp_log_text);
        logText.setMovementMethod(new ScrollingMovementMethod());

        btnStart.setOnClickListener(v -> onStartClicked());
        btnStop.setOnClickListener(v -> {
            McpService.stop(requireContext());
            // 稍后刷新状态
            mainHandler.postDelayed(this::refreshUi, 400);
        });
        urlText.setOnClickListener(v -> copyUrl());
        urlText.setOnLongClickListener(v -> {
            copyUrl();
            return true;
        });
        return view;
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
        appendLog("启动服务，端口 " + pendingPort + " …");
        McpService.start(requireContext(), pendingPort);
        // 服务启动是异步的：延迟刷新几次状态
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

    private void copyUrl() {
        CharSequence cs = urlText.getText();
        if (cs == null || cs.length() == 0) return;
        ClipboardManager cm = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("MCP URL", cs));
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
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
            urlText.setText(getString(R.string.mcp_url_detail, ip, port));
            urlText.setVisibility(View.VISIBLE);
            server.setLogListener(this::appendLog);
        } else {
            statusText.setText(R.string.mcp_stopped);
            urlText.setVisibility(View.GONE);
        }
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
