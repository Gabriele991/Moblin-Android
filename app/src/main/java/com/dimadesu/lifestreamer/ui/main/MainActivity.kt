/*
 * Copyright (C) 2021 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimadesu.lifestreamer.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.widget.PopupMenu
import android.widget.EditText
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.widget.CheckBox
import org.json.JSONArray
import org.json.JSONObject
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dimadesu.lifestreamer.R
import com.dimadesu.lifestreamer.databinding.ActivityMoblinShellBinding
import com.dimadesu.lifestreamer.models.StreamStatus
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.dimadesu.lifestreamer.rtmp.audio.MediaProjectionService
import com.dimadesu.lifestreamer.services.CameraStreamerService
import com.dimadesu.lifestreamer.ui.settings.SettingsActivity
import com.dimadesu.lifestreamer.ui.help.FaqHelpActivity
import com.dimadesu.lifestreamer.ui.help.KnownIssuesActivity
import com.dimadesu.lifestreamer.ui.help.RtmpHelpActivity
import com.dimadesu.lifestreamer.ui.help.SrtHelpActivity
import com.dimadesu.lifestreamer.ui.help.UvcHelpActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMoblinShellBinding
    private data class ScenePreset(val name: String, val usb: Boolean)
    private var embeddedChat: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification Quit starts a new activity when none is running.
        // Handle it here — onNewIntent is not called for a fresh launch —
        // and skip UI/service bind so we don't race teardown.
        if (intent?.action == CameraStreamerService.ACTION_EXIT_APP) {
            exitApp()
            return
        }

        binding = ActivityMoblinShellBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Note: Permissions (Camera, Microphone, Notifications) are requested by PreviewFragment.
        // Bluetooth permissions are requested on-demand when user taps the BT mic toggle.

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, PreviewFragment())
                .commitNow()
        }

        bindProperties()
        updateSourceIndicator()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle notification tap action to avoid re-creating activity and
        // triggering unnecessary view detach/attach which can race with camera.
        val action = intent.action
        if (action == CameraStreamerService.ACTION_EXIT_APP) {
            exitApp()
        } else if (action == CameraStreamerService.ACTION_OPEN_FROM_NOTIFICATION) {
            // If the PreviewFragment is already present, do nothing. If not,
            // ensure it's added without recreating the fragment stack.
            val current = supportFragmentManager.findFragmentById(R.id.container)
            if (current == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, PreviewFragment())
                    .commitNow()
            }
        }
    }

    private fun exitApp() {
        // Unbind first: a started+bound service ignores stopService() until
        // every client unbinds. PreviewViewModel otherwise unbinds only in
        // onCleared(), which runs after the activity is already finishing.
        (supportFragmentManager.findFragmentById(R.id.container) as? PreviewFragment)
            ?.prepareForExit()

        try {
            stopService(Intent(this, CameraStreamerService::class.java))
        } catch (_: Exception) {
            // Service might not be running, that's okay
        }
        try {
            stopService(Intent(this, MediaProjectionService::class.java))
        } catch (_: Exception) {
            // Optional companion service; ignore if absent
        }

        finishAndRemoveTask()
    }

    private fun bindProperties() {
        binding.actions.setOnClickListener {
            showPopup()
        }
        binding.scenesTab.setOnClickListener { showBasicScenePicker() }
        binding.chatTab.setOnClickListener { toggleChatOverlay() }
        binding.chatOverlayClose.setOnClickListener { closeChatOverlay() }
        binding.chatOverlayTitle.setOnClickListener { showTwitchChat() }
        binding.usbTab.setOnClickListener { showUsbMenu() }
        binding.controlsTab.setOnClickListener {
            val showing = activePreview()?.toggleAdvancedControls() ?: false
            binding.controlsTab.text = if (showing) "Nascondi" else "Strumenti"
        }
        binding.settingsTab.setOnClickListener { goToSettingsActivity() }
    }

    private fun activePreview(): PreviewFragment? =
        supportFragmentManager.findFragmentById(R.id.container) as? PreviewFragment

    fun updateLiveIndicator(status: StreamStatus) {
        val label = when (status) {
            StreamStatus.NOT_STREAMING -> "● OFFLINE"
            StreamStatus.STARTING -> "● AVVIO"
            StreamStatus.CONNECTING -> "● CONNESSIONE"
            StreamStatus.STREAMING -> "● LIVE"
            StreamStatus.ERROR -> "● ERRORE"
        }
        val color = when (status) {
            StreamStatus.STREAMING -> Color.rgb(92, 235, 135)
            StreamStatus.ERROR -> Color.rgb(255, 104, 104)
            StreamStatus.STARTING, StreamStatus.CONNECTING -> Color.rgb(255, 199, 94)
            StreamStatus.NOT_STREAMING -> Color.WHITE
        }
        binding.liveIndicator.text = label
        binding.liveIndicator.setTextColor(color)
    }

    fun updateSourceIndicator() {
        val scenes = loadScenes()
        val selected = getSharedPreferences("moblin_android", MODE_PRIVATE)
            .getInt("selected_scene", -1)
        val name = scenes.getOrNull(selected)
            ?.takeIf { it.usb == (activePreview()?.isUsbSceneSelected() == true) }
            ?.name
            ?: if (activePreview()?.isUsbSceneSelected() == true) "DJI USB" else "TELEFONO"
        binding.sourceIndicator.text = "SCENA  ·  ${name.uppercase()}"
    }

    private fun showBasicScenePicker() {
        val scenes = loadScenes()
        val selected = getSharedPreferences("moblin_android", MODE_PRIVATE)
            .getInt("selected_scene", -1)
        AlertDialog.Builder(this)
            .setTitle("Scene")
            .setSingleChoiceItems(scenes.map { it.name + if (it.usb) "  ·  DJI USB" else "  ·  TELEFONO" }.toTypedArray(), selected) { dialog, index ->
                activePreview()?.selectBasicScene(scenes[index].usb)
                getSharedPreferences("moblin_android", MODE_PRIVATE)
                    .edit().putInt("selected_scene", index).apply()
                updateSourceIndicator()
                dialog.dismiss()
            }
            .setNeutralButton("Gestisci scene") { _, _ -> showSceneManager() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun loadScenes(): MutableList<ScenePreset> {
        val raw = getSharedPreferences("moblin_android", MODE_PRIVATE)
            .getString("scene_presets", null) ?: return mutableListOf(
            ScenePreset("DJI Pocket IRL", true),
            ScenePreset("Camera Samsung", false)
        )
        return try {
            val input = JSONArray(raw)
            val scenes = mutableListOf<ScenePreset>()
            for (index in 0 until input.length()) {
                val preset = input.getJSONObject(index)
                scenes.add(ScenePreset(preset.getString("name"), preset.getBoolean("usb")))
            }
            if (scenes.isEmpty()) mutableListOf(ScenePreset("DJI Pocket IRL", true)) else scenes
        } catch (_: Exception) {
            mutableListOf(ScenePreset("DJI Pocket IRL", true), ScenePreset("Camera Samsung", false))
        }
    }

    private fun saveScenes(scenes: List<ScenePreset>) {
        val output = JSONArray()
        scenes.forEach { preset ->
            output.put(JSONObject().put("name", preset.name).put("usb", preset.usb))
        }
        getSharedPreferences("moblin_android", MODE_PRIVATE)
            .edit().putString("scene_presets", output.toString()).apply()
    }

    private fun showSceneManager() {
        val scenes = loadScenes()
        val options = scenes.map { "Modifica · ${it.name}" }.toMutableList()
        if (scenes.size < 8) options.add("+ Nuova scena")
        AlertDialog.Builder(this)
            .setTitle("Gestisci scene")
            .setItems(options.toTypedArray()) { _, index ->
                showSceneEditor(if (index >= scenes.size) null else index)
            }
            .setNegativeButton("Indietro") { _, _ -> showBasicScenePicker() }
            .show()
    }

    private fun showSceneEditor(index: Int?) {
        val scenes = loadScenes()
        val scene = index?.let { scenes[it] }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(38, 8, 38, 8)
        }
        val input = EditText(this).apply {
            hint = "Nome scena"
            setSingleLine(true)
            setText(scene?.name.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(40))
        }
        val useUsb = CheckBox(this).apply {
            text = "Fotocamera DJI tramite USB"
            isChecked = scene?.usb ?: true
        }
        fields.addView(input)
        fields.addView(useUsb)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (index == null) "Nuova scena" else "Modifica scena")
            .setView(fields)
            .setPositiveButton("Salva") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val updated = ScenePreset(name, useUsb.isChecked)
                    if (index == null) scenes.add(updated) else scenes[index] = updated
                    saveScenes(scenes)
                    updateSourceIndicator()
                }
                showSceneManager()
            }
            .setNegativeButton("Annulla") { _, _ -> showSceneManager() }
        if (index != null && scenes.size > 1) {
            dialog.setNeutralButton("Elimina") { _, _ ->
                scenes.removeAt(index)
                saveScenes(scenes)
                getSharedPreferences("moblin_android", MODE_PRIVATE)
                    .edit().putInt("selected_scene", -1).apply()
                updateSourceIndicator()
                showSceneManager()
            }
        }
        dialog.show()
    }

    private fun showUsbMenu() {
        val enabled = activePreview()?.isUsbSceneSelected() == true
        val actions = arrayOf(
            if (enabled) "Disattiva sorgente USB" else "Attiva DJI come sorgente USB",
            "Test formato USB + FPS reali"
        )
        AlertDialog.Builder(this)
            .setTitle("DJI Pocket 4 Pro · USB")
            .setItems(actions) { _, index ->
                when (index) {
                    0 -> {
                        activePreview()?.selectBasicScene(!enabled)
                        getSharedPreferences("moblin_android", MODE_PRIVATE)
                            .edit().putInt("selected_scene", -1).apply()
                        updateSourceIndicator()
                    }
                    1 -> if (enabled) {
                        AlertDialog.Builder(this)
                            .setMessage("Prima disattiva DJI USB come sorgente live: il test USB deve aprire la fotocamera in esclusiva.")
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        startActivity(Intent(this, com.dimadesu.lifestreamer.uvc.UvcTestActivity::class.java))
                    }
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showTwitchChat() {
        val prefs = getSharedPreferences("moblin_android", MODE_PRIVATE)
        val channel = prefs.getString("twitch_channel", "asapgringoo") ?: "asapgringoo"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 20, 28))
        }
        val heading = TextView(this).apply {
            text = "CHAT  ·  #$channel"
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(20, 24, 20, 20)
            setOnClickListener { showChannelEditor() }
        }
        root.addView(heading)
        val chatView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("https://www.twitch.tv/popout/${Uri.encode(channel)}/chat?popout=")
        }
        root.addView(chatView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.6f).toInt()
        ))
        val panel = BottomSheetDialog(this)
        panel.setContentView(root)
        panel.setOnDismissListener { chatView.destroy() }
        panel.show()
    }

    private fun toggleChatOverlay() {
        if (binding.chatOverlay.visibility == View.VISIBLE) {
            closeChatOverlay()
            return
        }
        val channel = getSharedPreferences("moblin_android", MODE_PRIVATE)
            .getString("twitch_channel", "asapgringoo") ?: "asapgringoo"
        binding.chatOverlayTitle.text = "CHAT TWITCH  ·  #$channel"
        if (embeddedChat == null) {
            val chat = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
            }
            embeddedChat = chat
            binding.chatOverlayContent.addView(chat)
        }
        embeddedChat?.loadUrl("https://www.twitch.tv/popout/${Uri.encode(channel)}/chat?popout=")
        binding.chatOverlay.visibility = View.VISIBLE
    }

    private fun closeChatOverlay() {
        binding.chatOverlay.visibility = View.GONE
        embeddedChat?.loadUrl("about:blank")
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            embeddedChat?.let { chat ->
                binding.chatOverlayContent.removeView(chat)
                chat.destroy()
            }
        }
        embeddedChat = null
        super.onDestroy()
    }

    private fun showChannelEditor() {
        val prefs = getSharedPreferences("moblin_android", MODE_PRIVATE)
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(prefs.getString("twitch_channel", "asapgringoo"))
            hint = "Canale Twitch"
        }
        AlertDialog.Builder(this)
            .setTitle("Nome canale Twitch")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val channel = input.text.toString().trim().removePrefix("#")
                if (channel.matches(Regex("[A-Za-z0-9_]{2,25}"))) {
                    prefs.edit().putString("twitch_channel", channel).apply()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showPopup() {
        val popup = PopupMenu(this, binding.actions)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.actions, popup.menu)
        popup.show()
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_settings -> {
                    goToSettingsActivity()
                    true
                }
                R.id.action_rtmp_help -> {
                    goToRtmpHelpActivity()
                    true
                }
                R.id.action_srt_help -> {
                    goToSrtHelpActivity()
                    true
                }
                R.id.action_uvc_help -> {
                    goToUvcHelpActivity()
                    true
                }
                R.id.action_faq -> {
                    goToFaqHelpActivity()
                    true
                }
                R.id.action_known_issues -> {
                    goToKnownIssuesActivity()
                    true
                }
                R.id.action_quit -> {
                    exitApp()
                    true
                }
                else -> {
                    Log.e(TAG, "Unknown menu item ${it.itemId}")
                    false
                }
            }
        }
    }

    private fun goToSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun goToRtmpHelpActivity() {
        val intent = Intent(this, RtmpHelpActivity::class.java)
        startActivity(intent)
    }

    private fun goToSrtHelpActivity() {
        val intent = Intent(this, SrtHelpActivity::class.java)
        startActivity(intent)
    }

    private fun goToUvcHelpActivity() {
        val intent = Intent(this, UvcHelpActivity::class.java)
        startActivity(intent)
    }

    private fun goToFaqHelpActivity() {
        val intent = Intent(this, FaqHelpActivity::class.java)
        startActivity(intent)
    }

    private fun goToKnownIssuesActivity() {
        val intent = Intent(this, KnownIssuesActivity::class.java)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.actions, menu)
        return true
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
