package com.hussein.aiagent

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private var studyTimer: CountDownTimer? = null
    private var studySessionTotalMinutes: Int = 0
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var statsText: TextView
    private lateinit var miniStatsText: TextView
    private lateinit var strictModeButton: Button
    private lateinit var tasksContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var treeText: TextView
    private val treeStages = listOf("🌱", "🌿", "🪴", "🌳", "🌲")

    // ---- الحاويات (الصفحات) ----
    private lateinit var pageHome: LinearLayout
    private lateinit var pageFocus: LinearLayout
    private lateinit var pageTasks: LinearLayout
    private lateinit var pageNotes: LinearLayout
    private lateinit var pageSettings: LinearLayout
    private lateinit var tabButtons: List<Button>

    // ---- ألوان الثيم الفضائي ----
    private val bgSpace = Color.parseColor("#0A0B1E")
    private val panelSpace = Color.parseColor("#1C1F3D")
    private val neonPurple = Color.parseColor("#8B5CF6")
    private val neonCyan = Color.parseColor("#22D3EE")
    private val neonGreen = Color.parseColor("#34D399")
    private val neonRed = Color.parseColor("#F87171")
    private val textPrimary = Color.parseColor("#F1F5FF")
    private val textSecondary = Color.parseColor("#9CA3C7")
    private val tabInactive = Color.parseColor("#1C1F3D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentPrefs.init(this)
        TaskManager.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgSpace)
        }

        // ---- رأس ثابت فوگ دائماً ----
        val header = TextView(this).apply {
            text = "🛸 صگر"
            textSize = 22f
            setTextColor(neonCyan)
            gravity = Gravity.CENTER
            setPadding(20, 50, 20, 5)
        }
        miniStatsText = TextView(this).apply {
            text = miniStatsLine()
            textSize = 12f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setPadding(20, 0, 20, 15)
        }

        // ---- شريط التبويبات ----
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 0, 10, 10)
        }

        // ---- محتوى الصفحة الحالي (يتغير حسب التبويب) ----
        val pageHolder = FrameLayout(this)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // ===================== صفحة الرئيسية =====================
        pageHome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 40)
        }
        statusText = TextView(this).apply {
            text = "الحالة: جاهز للعمل ⭐"
            textSize = 16f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        treeText = TextView(this).apply {
            text = "🌱 ما بعد بدأت جلسة تركيز"
            textSize = 20f
            setTextColor(neonGreen)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        statsText = TextView(this).apply {
            text = statsLine()
            textSize = 14f
            setTextColor(textSecondary)
            background = roundedPanel(panelSpace, neonPurple)
            setPadding(25, 20, 25, 20)
        }
        val chatBox = TextView(this).apply {
            text = "💬 سجل الأوامر:\n- صيح (يا صگر) أو اكتب أمرك جوة..."
            textSize = 14f
            setTextColor(textPrimary)
            background = roundedPanel(panelSpace, neonCyan)
            setPadding(25, 25, 25, 25)
        }
        VoiceListenerService.lastRecognizedText = { userMsg, reply ->
            chatBox.text = "🗣 أنت: $userMsg\n🛸 صگر: $reply"
            refreshStats()
            refreshTasksUI()
        }
        val chatInput = EditText(this).apply {
            hint = "اكتب أي أمر لصگر هنا..."
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
        }
        val btnSendChat = spaceButton("إرسال لصگر", neonPurple) {
            val msg = chatInput.text.toString().trim()
            if (msg.isNotEmpty()) {
                chatInput.setText("")
                val handled = AgentAccessibilityService.instance?.handleFastCommand(msg) ?: false
                chatBox.text = if (handled) {
                    "🗣 أنت: $msg\n🛸 صگر: ⚡ تم تنفيذ الأمر فوراً!"
                } else {
                    val reply = "ما فهمت الأمر يا حسين، جرب تگول أمر ثاني."
                    VoiceListenerService.speakOut(reply)
                    "🗣 أنت: $msg\n🛸 صگر: $reply"
                }
                refreshStats()
                refreshTasksUI()
            }
        }
        val btnStartVoice = spaceButton("🎙 تشغيل صگر بالخلفية دائماً", neonCyan) {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            ActivityCompat.requestPermissions(this@MainActivity, perms.toTypedArray(), 1)
            val serviceIntent = Intent(this@MainActivity, VoiceListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            statusText.text = "🛸 صگر شغال بالخلفية الآن! صيح (يا صگر)"
        }
        pageHome.addView(statusText)
        pageHome.addView(treeText)
        pageHome.addView(statsText)
        pageHome.addView(spacer())
        pageHome.addView(chatBox)
        pageHome.addView(chatInput)
        pageHome.addView(btnSendChat)
        pageHome.addView(spacer())
        pageHome.addView(btnStartVoice)

        // ربط موت الشجرة بالواجهة (مثل Forest: فتح تطبيق مشتت وقت التركيز = موت الشجرة)
        AgentAccessibilityService.onTreeKilled = { appName ->
            runOnUiThread {
                treeText.text = "🥀 ماتت شجرتك! فتحت $appName وقت التركيز."
                statusText.text = "💀 خسرت الجلسة. روح لتبويب التركيز وابدأ وحدة جديدة."
                studyTimer?.cancel()
                AgentAccessibilityService.isStudyMode = false
                AgentAccessibilityService.studyRemainingMinutes = 0
                refreshStats()
            }
        }

        // ===================== صفحة التركيز =====================
        pageFocus = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 40)
        }
        val focusTreeText = TextView(this).apply {
            text = "🌱 ما بعد بدأت جلسة"
            textSize = 20f
            setTextColor(neonGreen)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        val sectionNoise = sectionTitle("🎧 صوت التركيز (ضوضاء بيضاء هادئة شبيهة بالمطر)")
        val btnNoiseOn = spaceButton("شغل صوت التركيز 🌧", neonCyan) {
            val svc = VoiceListenerService.instance
            if (svc == null) {
                statusText.text = "شغل صگر بالخلفية أول (من تبويب الرئيسية)!"
            } else {
                svc.startWhiteNoise()
            }
        }
        val btnNoiseOff = spaceButton("وقف صوت التركيز", neonRed) {
            VoiceListenerService.instance?.stopWhiteNoise()
        }
        val sectionDuration = sectionTitle("⏱ مدة جلسة التركيز")
        val durationRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var selectedDuration = 25
        val durationLabel = TextView(this).apply {
            text = "المدة المختارة: $selectedDuration دقيقة"
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        for (mins in listOf(15, 25, 45, 60)) {
            val b = spaceButton("$mins د", neonPurple) {
                selectedDuration = mins
                durationLabel.text = "المدة المختارة: $selectedDuration دقيقة"
            }
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(5, 12, 5, 0)
            }
            durationRow.addView(b)
        }
        val sectionFocus = sectionTitle("🔒 وضع التركيز (شجرتك تموت لو تشتت)")
        val btnStartStudy = spaceButton("▶️ بدء جلسة التركيز", neonRed) {
            startStudySession(selectedDuration, statusText, treeText, focusTreeText)
        }
        val btnStopStudy = spaceButton("⏹ إنهاء الجلسة وإرجاع الجهاز طبيعي", neonGreen) {
            stopStudySession(statusText)
            treeText.text = "🌱 ما بعد بدأت جلسة تركيز"
            focusTreeText.text = "🌱 ما بعد بدأت جلسة"
        }
        strictModeButton = spaceButton(strictModeLabel(), neonPurple) {
            AgentPrefs.strictMode = !AgentPrefs.strictMode
            strictModeButton.text = strictModeLabel()
            VoiceListenerService.speakOut(
                if (AgentPrefs.strictMode) "فعّلت وضع التركيز الصارم." else "لغيت وضع التركيز الصارم."
            )
        }
        pageFocus.addView(focusTreeText)
        pageFocus.addView(sectionNoise)
        pageFocus.addView(btnNoiseOn)
        pageFocus.addView(btnNoiseOff)
        pageFocus.addView(sectionDuration)
        pageFocus.addView(durationLabel)
        pageFocus.addView(durationRow)
        pageFocus.addView(sectionFocus)
        pageFocus.addView(btnStartStudy)
        pageFocus.addView(btnStopStudy)
        pageFocus.addView(strictModeButton)

        // ===================== صفحة المهام =====================
        pageTasks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 40)
        }
        val taskTitleInput = EditText(this).apply {
            hint = "اسم المهمة (مثلاً: ذاكر الفصل الثالث)"
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
        }
        val taskDeadlineInput = EditText(this).apply {
            hint = "الموعد النهائي (اختياري، مثلاً: بكرة الساعة 5)"
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
        }
        val btnAddTask = spaceButton("➕ إضافة مهمة", neonGreen) {
            val title = taskTitleInput.text.toString().trim()
            if (title.isNotEmpty()) {
                TaskManager.addTask(title, taskDeadlineInput.text.toString().trim())
                taskTitleInput.setText("")
                taskDeadlineInput.setText("")
                refreshTasksUI()
            }
        }
        tasksContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pageTasks.addView(sectionTitle("✅ مهامي"))
        pageTasks.addView(taskTitleInput)
        pageTasks.addView(taskDeadlineInput)
        pageTasks.addView(btnAddTask)
        pageTasks.addView(tasksContainer)

        // ===================== صفحة الملاحظات =====================
        pageNotes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 40)
        }
        val notesInput = EditText(this).apply {
            hint = "اكتب خطتك الدراسية، جدولك، أو أي ملاحظة تريد تحفظها..."
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            setText(AgentPrefs.studyNotes)
        }
        val btnSaveNotes = spaceButton("💾 حفظ الملاحظات", neonCyan) {
            AgentPrefs.studyNotes = notesInput.text.toString()
            statusText.text = "تم حفظ ملاحظاتك بذاكرة صگر الدائمة!"
        }
        pageNotes.addView(sectionTitle("📝 ملاحظاتي وخطتي الدراسية"))
        pageNotes.addView(notesInput)
        pageNotes.addView(btnSaveNotes)

        // ===================== صفحة الإعدادات =====================
        pageSettings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 40)
        }
        val pinInput = EditText(this).apply {
            hint = "اكتب رمز قفل جهازك هنا (لفتحه بالصوت)"
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
            setText(AgentPrefs.pinCode)
        }
        val btnSavePin = spaceButton("حفظ رمز القفل", neonCyan) {
            AgentPrefs.pinCode = pinInput.text.toString().trim()
            statusText.text = "تم حفظ رمز الجهاز بذاكرة صگر الدائمة!"
        }
        val btnOverlay = spaceButton("تفعيل الظهور فوق التطبيقات", neonPurple) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val btnAdmin = spaceButton("تفعيل صلاحية قفل الشاشة", neonPurple) {
            val comp = ComponentName(this@MainActivity, AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
            }
            startActivity(intent)
        }
        val btnAccess = spaceButton("تفعيل التحكم الكامل (Accessibility)", neonPurple) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        pageSettings.addView(sectionTitle("⚙️ الإعدادات والصلاحيات"))
        pageSettings.addView(pinInput)
        pageSettings.addView(btnSavePin)
        pageSettings.addView(btnOverlay)
        pageSettings.addView(btnAdmin)
        pageSettings.addView(btnAccess)

        // ---- أزرار التبويبات ----
        val tabPages = listOf(pageHome, pageFocus, pageTasks, pageNotes, pageSettings)
        val tabLabels = listOf("🏠", "🔒", "✅", "📝", "⚙️")
        val buttons = mutableListOf<Button>()
        for (i in tabLabels.indices) {
            val tabBtn = Button(this).apply {
                text = tabLabels[i]
                textSize = 18f
                setTextColor(Color.WHITE)
                background = roundedPanel(tabInactive, neonPurple)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(4, 0, 4, 0)
                }
            }
            buttons.add(tabBtn)
            tabRow.addView(tabBtn)
        }
        tabButtons = buttons
        for (i in buttons.indices) {
            buttons[i].setOnClickListener { showPage(i, tabPages, buttons) }
        }

        scroll.addView(FrameLayout(this).apply {
            addView(pageHome); addView(pageFocus); addView(pageTasks); addView(pageNotes); addView(pageSettings)
        })

        root.addView(header)
        root.addView(miniStatsText)
        root.addView(tabRow)
        root.addView(scroll)

        setContentView(root)
        showPage(0, tabPages, buttons)
        refreshTasksUI()
    }

    // ---- التبديل بين الصفحات ----
    private fun showPage(index: Int, pages: List<LinearLayout>, buttons: List<Button>) {
        for (i in pages.indices) {
            pages[i].visibility = if (i == index) View.VISIBLE else View.GONE
            buttons[i].background = roundedPanel(
                if (i == index) neonPurple else tabInactive,
                neonPurple
            )
        }
    }

    // ---- أدوات بناء الواجهة الفضائية ----
    private fun roundedPanel(bgColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 24f
            setStroke(2, strokeColor)
        }
    }

    private fun spaceButton(label: String, accent: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            background = roundedPanel(accent, accent)
            setPadding(20, 24, 20, 24)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 12, 0, 0)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(neonCyan)
            setPadding(0, 30, 0, 5)
        }
    }

    private fun spacer(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20)
        }
    }

    private fun miniStatsLine(): String {
        return "📊 ${AgentPrefs.sessionsCompleted} جلسة | 🌲 ${AgentPrefs.treesGrown} | 🥀 ${AgentPrefs.treesDead} | ✅ ${TaskManager.pendingCount()} مهمة باقية"
    }

    private fun statsLine(): String {
        return "📊 ${AgentPrefs.sessionsCompleted} جلسة | ${AgentPrefs.totalFocusMinutes} دقيقة تركيز | 🌲 ${AgentPrefs.treesGrown} شجرة ناجحة | 🥀 ${AgentPrefs.treesDead} ماتت | ${AgentPrefs.blockedAttempts} محاولة تشتت"
    }

    private fun refreshStats() {
        statsText.text = statsLine()
        miniStatsText.text = miniStatsLine()
    }

    private fun strictModeLabel(): String {
        val state = if (AgentPrefs.strictMode) "مفعّل ✅" else "متوقف ⛔"
        return "🔥 وضع التركيز الصارم: $state"
    }

    // ---- بناء قائمة المهام ديناميكياً ----
    private fun refreshTasksUI() {
        tasksContainer.removeAllViews()
        val tasks = TaskManager.getTasks()
        if (tasks.isEmpty()) {
            val empty = TextView(this).apply {
                text = "ما عندك مهام هسة، ضيف وحدة فوگ!"
                setTextColor(textSecondary)
                setPadding(10, 10, 10, 10)
            }
            tasksContainer.addView(empty)
        } else {
            for (task in tasks.sortedBy { it.completed }) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(10, 10, 10, 10)
                }
                val check = CheckBox(this).apply {
                    isChecked = task.completed
                    setOnCheckedChangeListener { _, _ ->
                        TaskManager.toggleComplete(task.id)
                        refreshTasksUI()
                        refreshStats()
                    }
                }
                val label = TextView(this).apply {
                    val deadlinePart = if (task.deadline.isNotBlank()) " ⏰ ${task.deadline}" else ""
                    text = "${task.title}$deadlinePart"
                    setTextColor(if (task.completed) textSecondary else textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val delete = TextView(this).apply {
                    text = "🗑"
                    setPadding(20, 0, 10, 0)
                    setOnClickListener {
                        TaskManager.deleteTask(task.id)
                        refreshTasksUI()
                        refreshStats()
                    }
                }
                row.addView(check)
                row.addView(label)
                row.addView(delete)
                tasksContainer.addView(row)
            }
        }
        refreshStats()
    }

    private fun startStudySession(minutes: Int, statusText: TextView, treeText: TextView, focusTreeText: TextView) {
        AgentAccessibilityService.isStudyMode = true
        AgentAccessibilityService.studyRemainingMinutes = minutes
        studySessionTotalMinutes = minutes
        val startMsg = "🌱 زرعت شجرة جديدة! لا تفتح تطبيقات مشتتة لا تموت"
        treeText.text = startMsg
        focusTreeText.text = startMsg
        VoiceListenerService.speakOut("بلشت وقت القراءة يا حسين لمدة $minutes دقيقة! زرعت شجرتك، لا تخليها تموت. شد حيلك!")

        studyTimer?.cancel()
        studyTimer = object : CountDownTimer(minutes * 60 * 1000L, 60 * 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minsLeft = (millisUntilFinished / 60000).toInt()
                AgentAccessibilityService.studyRemainingMinutes = minsLeft
                statusText.text = "🔒 وضع التركيز شغال! باقي: $minsLeft دقيقة"

                val elapsedFraction = 1f - (millisUntilFinished.toFloat() / (studySessionTotalMinutes * 60 * 1000f))
                val stageIndex = (elapsedFraction * (treeStages.size - 1)).toInt().coerceIn(0, treeStages.size - 1)
                val growthMsg = "${treeStages[stageIndex]} شجرتك تنمو... ${(elapsedFraction * 100).toInt()}%"
                treeText.text = growthMsg
                focusTreeText.text = growthMsg

                if (minsLeft == 5) {
                    VoiceListenerService.speakOut("عفيه عليك يا بطل، بعدلك 5 دقايق بس وتكمل، شد حيلك!")
                }
            }

            override fun onFinish() {
                AgentPrefs.addFocusMinutes(studySessionTotalMinutes)
                AgentPrefs.sessionsCompleted += 1
                AgentPrefs.treesGrown += 1
                val doneMsg = "🌲 عاشت شجرتك كاملة! فخورين فيك"
                treeText.text = doneMsg
                focusTreeText.text = doneMsg
                stopStudySession(statusText)
                VoiceListenerService.speakOut("خلصت الجلسة كاملة يا بطل! شجرتك عاشت وكبرت، فخور فيك.")
            }
        }.start()
    }

    private fun stopStudySession(statusText: TextView) {
        studyTimer?.cancel()
        val elapsed = studySessionTotalMinutes - AgentAccessibilityService.studyRemainingMinutes
        if (AgentAccessibilityService.isStudyMode && elapsed > 0 && AgentAccessibilityService.studyRemainingMinutes > 0) {
            AgentPrefs.addFocusMinutes(elapsed)
        }
        AgentAccessibilityService.isStudyMode = false
        AgentAccessibilityService.studyRemainingMinutes = 0
        AgentAccessibilityService.instance?.removeLockOverlay()
        statusText.text = "🔓 الوضع طبيعي الآن (التطبيقات مفتوحة)"
        refreshStats()
        VoiceListenerService.speakOut("عاشت إيدك يا حسين، رجعت الجهاز للوضع الطبيعي.")
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }
}
