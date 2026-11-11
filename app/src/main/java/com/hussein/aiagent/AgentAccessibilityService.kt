package com.hussein.aiagent

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AgentAccessibilityService? = null
        var isStudyMode: Boolean = false
        var studyRemainingMinutes: Int = 0
        val tempAllowedPackages = mutableMapOf<String, Long>()
        // يتنادى لما الشجرة تموت (المستخدم فتح تطبيق مشتت وقت التركيز) — تربطه MainActivity
        var onTreeKilled: ((String) -> Unit)? = null
    }

    private var windowManager: WindowManager? = null
    private var lockOverlayView: LinearLayout? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastBlockedPkg: String = ""
    private var lastBlockTime: Long = 0
    private var flashlightOn: Boolean = false

    // ردود صگر الفضائية المتنوعة (شخصية الحارس القادم من الفضاء)
    private val greetingReplies = listOf(
        "هبطت من مدارك حالاً يا حسين، صگر حاضر!",
        "أني هنا يا قائد، صگر يراقبك من فوگ!",
        "وصلت الإشارة، صگر جاهز لأمرك يا حسين!"
    )

    private val blockedExcuseReplies = listOf(
        "عذر ما يقنع حارس فضائي يا حسين، ارجع لكتابك!",
        "محطة التركيز ما تفتح بهالعذر، جرب عذر أقوى!",
        "سفينتك لسه بوضع القراءة، هذا العذر ما يكفي!"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentPrefs.init(this)
        TaskManager.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isStudyMode) {
            removeLockOverlay()
            return
        }

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (isSystemOrAllowedPackage(pkg)) return

            val now = System.currentTimeMillis()
            val allowedUntil = tempAllowedPackages[pkg] ?: 0L
            if (now < allowedUntil) return

            // منع التكرار المزعج بالصوت
            if (pkg != lastBlockedPkg || (now - lastBlockTime) > 6000) {
                lastBlockedPkg = pkg
                lastBlockTime = now
                AgentPrefs.incrementBlockedAttempts()
                showNegotiationOverlay(pkg)
            }
        }
    }

    private fun isSystemOrAllowedPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.hussein.aiagent" ||
                lower.contains("systemui") ||
                lower.contains("launcher") ||
                lower.contains("home") ||
                lower.contains("miui.home") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("gboard") ||
                lower.contains("permissioncontroller") ||
                lower.contains("packageinstaller") ||
                lower == "android" ||
                // تطبيقات أساسية وضرورية للطوارئ والأمان - تظل مفتوحة حتى وقت التركيز
                lower.contains("dialer") ||
                lower.contains("contacts") ||
                lower.contains("mms") ||
                lower.contains("messaging") ||
                lower.contains("camera") ||
                lower.contains("clock") ||
                lower.contains("deskclock") ||
                lower.contains("emergency")
        // ملاحظة: ما حطينا "settings" بالقائمة عمداً، لأن فتحها يخلي حسين يگدر يعطل
        // خدمة Accessibility نفسها من جوة الإعدادات ويتحايل على كل نظام الحظر
    }

    private fun showNegotiationOverlay(blockedPkg: String) {
        if (!Settings.canDrawOverlays(this)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            VoiceListenerService.speakOut("حسين! أنت بوقت قراءة، ممنوع تفتح التطبيقات هسة.")
            return
        }

        Handler(Looper.getMainLooper()).post {
            if (lockOverlayView != null) return@post

            val appName = try {
                val info = packageManager.getApplicationInfo(blockedPkg, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                blockedPkg
            }

            VoiceListenerService.speakOut("أوكف يا حسين! أنت بوقت قراءة وباقي $studyRemainingMinutes دقيقة. ليش فتحت $appName؟ اكتب عذرك لو ارجع لدراستك.")

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER

            val panelBg = GradientDrawable().apply {
                setColor(Color.parseColor("#F20A0B1E"))
                cornerRadius = 36f
                setStroke(3, Color.parseColor("#8B5CF6"))
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = panelBg
                gravity = Gravity.CENTER
                setPadding(60, 60, 60, 60)
            }

            val title = TextView(this).apply {
                text = "🛸 صگر الفضائي: المحطة مقفولة!\nباقي لوقت التركيز: $studyRemainingMinutes دقيقة"
                textSize = 22f
                setTextColor(Color.parseColor("#F1F5FF"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 30)
            }

            val aiReplyText = TextView(this).apply {
                text = "تطبيق ($appName) ممنوع وقت التركيز. شنو عذرك؟"
                textSize = 17f
                setTextColor(Color.parseColor("#22D3EE"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 30)
            }

            val excuseInput = EditText(this).apply {
                hint = "اكتب عذرك هنا لصگر (مثلاً: أريد أطلع ملزمة)..."
                setHintTextColor(Color.parseColor("#9CA3C7"))
                setTextColor(Color.parseColor("#F1F5FF"))
            }

            val btnNegotiate = Button(this).apply {
                text = "إرسال العذر لصگر"
                setOnClickListener {
                    val excuse = excuseInput.text.toString().trim()
                    if (excuse.isEmpty()) {
                        aiReplyText.text = "اكتب عذر أول شي!"
                        return@setOnClickListener
                    }
                    val (allowed, reply) = evaluateExcuse(excuse, appName)
                    aiReplyText.text = reply
                    VoiceListenerService.speakOut(reply)
                    if (allowed) {
                        // فتح التطبيق لمدة دقيقتين
                        tempAllowedPackages[blockedPkg] = System.currentTimeMillis() + (2 * 60 * 1000L)
                        removeLockOverlay()
                        // مثل تطبيق Forest: فتح تطبيق مشتت وقت التركيز = موت الشجرة
                        if (isStudyMode) {
                            AgentPrefs.treesDead += 1
                            onTreeKilled?.invoke(appName)
                        }
                    }
                }
            }

            val btnGoBack = Button(this).apply {
                text = "رجوع للدراسة (إغلاق التطبيق)"
                setOnClickListener {
                    removeLockOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }

            layout.addView(title)
            layout.addView(aiReplyText)
            layout.addView(excuseInput)
            layout.addView(btnNegotiate)
            layout.addView(btnGoBack)

            lockOverlayView = layout
            try {
                windowManager?.addView(lockOverlayView, params)
            } catch (_: Exception) {}
        }
    }

    // فحص العذر محلياً بدون أي اتصال إنترنت أو ذكاء اصطناعي خارجي
    private val allowedExcuseKeywords = listOf(
        "واجب", "ملزمة", "استاذ", "أستاذ", "دكتور", "امتحان", "اختبار",
        "طوارئ", "مستعجل", "مدرسة", "محاضرة", "بحث", "تطبيق دراسي"
    )

    private fun evaluateExcuse(excuse: String, appName: String): Pair<Boolean, String> {
        val lower = excuse.lowercase()
        val isValid = allowedExcuseKeywords.any { lower.contains(it) }
        return if (isValid) {
            true to "خوش عذر، فتحتلك $appName لمدة دقيقتين بس لا تتأخر!"
        } else {
            false to blockedExcuseReplies.random()
        }
    }

    fun removeLockOverlay() {
        Handler(Looper.getMainLooper()).post {
            if (lockOverlayView != null) {
                try {
                    windowManager?.removeView(lockOverlayView)
                } catch (_: Exception) {}
                lockOverlayView = null
            }
        }
    }

    override fun onInterrupt() {}

    // محرك التنفيذ الفوري السريع (Zero-Latency) بدون انتظار النت!
    fun handleFastCommand(rawText: String): Boolean {
        val cmd = rawText.lowercase().trim()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1. نداء الاسم (صگر)
        if (cmd == "صكر" || cmd == "صقر" || cmd == "يا صكر" || cmd == "يا صقر" || cmd == "حسين") {
            VoiceListenerService.speakOut(greetingReplies.random())
            return true
        }

        // 2. الساعة بيش
        if (cmd.contains("الساعه بيش") || cmd.contains("الساعة بيش") || cmd.contains("شكد الساعه")) {
            val time = SimpleDateFormat("hh:mm a", Locale("ar", "IQ")).format(Date())
            VoiceListenerService.speakOut("الساعة هسة $time يا حسين.")
            return true
        }

        // 3. التحكم بالصوت فوري
        if (cmd.contains("نصي الصوت") || cmd.contains("انصي الصوت") || cmd.contains("خفض الصوت")) {
            for (i in 1..3) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            VoiceListenerService.speakOut("نصيت الصوت.")
            return true
        }
        if (cmd.contains("علي الصوت") || cmd.contains("ارفع الصوت") || cmd.contains("عل الصوت")) {
            for (i in 1..3) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            VoiceListenerService.speakOut("عليت الصوت.")
            return true
        }

        // 4. قفل الجهاز فوري
        if (cmd.contains("اقفل الجهاز") || cmd.contains("طفي الشاشه") || cmd.contains("اقفل الشاشه")) {
            VoiceListenerService.speakOut("تم قفل الجهاز.")
            lockDeviceNow()
            return true
        }

        // 5. فتح الرمز فوري
        if (cmd.contains("افتح الرمز") || cmd.contains("افتح الجهاز") || cmd.contains("افتح القفل")) {
            VoiceListenerService.speakOut("جاري فتح الرمز.")
            enterPinAutomatically(AgentPrefs.pinCode)
            return true
        }

        // 6. بدء أو إنهاء القراءة بالصوت
        if (cmd.contains("بلش قرايه") || cmd.contains("ابدي قرايه") || cmd.contains("قفل التطبيقات")) {
            isStudyMode = true
            studyRemainingMinutes = 25
            VoiceListenerService.speakOut("بلشت وقت القراءة وقفلت التطبيقات، شد حيلك يا بطل!")
            return true
        }
        if (cmd.contains("خلصت قرايه") || cmd.contains("كملت قرايه") || cmd.contains("افتح التطبيقات")) {
            isStudyMode = false
            studyRemainingMinutes = 0
            removeLockOverlay()
            VoiceListenerService.speakOut("عاشت إيدك يا حسين! لغيت قفل الدراسة ورجع الجهاز طبيعي.")
            return true
        }

        // 7. الخروج والرجوع فوري
        if (cmd.contains("اخرج") || cmd.contains("اطلع بره") || cmd.contains("الرئيسيه")) {
            removeLockOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
            VoiceListenerService.speakOut("طلعت للشاشة الرئيسية.")
            return true
        }
        if (cmd.contains("ارجع") || cmd.contains("ليوره")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        // 8. فتح أي تطبيق فوراً بالاسم (يوگف بوضع التركيز الصارم)
        if (cmd.contains("افتح ") || cmd.contains("ادخل ") || cmd.contains("شغل ")) {
            if (isStudyMode && AgentPrefs.strictMode) {
                VoiceListenerService.speakOut("وضع التركيز الصارم شغال، ما أگدر أفتحلك تطبيقات هسة يا حسين!")
                return true
            }
            val opened = openAppByName(cmd)
            if (opened) return true
        }

        // 9. نسبة البطارية
        if (cmd.contains("نسبة البطاريه") || cmd.contains("البطاريه") || cmd.contains("شكد البطاريه")) {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            VoiceListenerService.speakOut("البطارية هسة عندها $level بالمية يا حسين.")
            return true
        }

        // 10. تشغيل/إطفاء الفلاش
        if (cmd.contains("شغل الفلاش") || cmd.contains("ولع الفلاش") || cmd.contains("طفي الفلاش")) {
            val turnOn = !cmd.contains("طفي")
            setFlashlight(turnOn)
            VoiceListenerService.speakOut(if (turnOn) "شغلت الفلاش." else "طفيت الفلاش.")
            return true
        }

        // 11. تقرير الإحصائيات
        if (cmd.contains("تقريري") || cmd.contains("احصائياتي") || cmd.contains("إحصائياتي") || cmd.contains("شلون أدائي")) {
            VoiceListenerService.speakOut(AgentPrefs.statsReport())
            return true
        }

        // 12. تفعيل/إلغاء وضع التركيز الصارم
        if (cmd.contains("فعل الوضع الصارم") || cmd.contains("شغل الوضع الصارم")) {
            AgentPrefs.strictMode = true
            VoiceListenerService.speakOut("فعّلت وضع التركيز الصارم، ما راح تگدر تفتح أي تطبيق وقت القراءة.")
            return true
        }
        if (cmd.contains("الغي الوضع الصارم") || cmd.contains("طفي الوضع الصارم")) {
            AgentPrefs.strictMode = false
            VoiceListenerService.speakOut("لغيت وضع التركيز الصارم.")
            return true
        }

        // 13. إضافة مهمة جديدة (مثل Todoist) - "ضيف مهمة ..." أو "مهمة جديدة ..."
        if (cmd.contains("ضيف مهمه") || cmd.contains("اضافه مهمه") || cmd.contains("مهمه جديده")) {
            val title = cmd.substringAfter("مهمه").trim()
            if (title.isNotEmpty()) {
                TaskManager.addTask(title)
                VoiceListenerService.speakOut("ضفت المهمة: $title")
            } else {
                VoiceListenerService.speakOut("گلي شنو المهمة اللي تريد تضيفها بعد كلمة مهمة.")
            }
            return true
        }

        // 14. عدد المهام الباقية
        if (cmd.contains("شكد مهمه") || cmd.contains("مهامي") || cmd.contains("شنو مهامي")) {
            val pending = TaskManager.pendingCount()
            VoiceListenerService.speakOut(
                if (pending == 0) "ما عندك مهام باقية، فاضي تماماً يا بطل!"
                else "باقيلك $pending مهمة لسه ما خلصتها."
            )
            return true
        }

        // 15. تشغيل/إيقاف صوت التركيز (ضوضاء بيضاء هادئة شبيهة بالمطر)
        if (cmd.contains("شغل صوت التركيز") || cmd.contains("صوت المطر") || cmd.contains("شغل الضوضاء")) {
            VoiceListenerService.instance?.startWhiteNoise()
            VoiceListenerService.speakOut("شغلت صوت التركيز، استانس بيه وركز.")
            return true
        }
        if (cmd.contains("طفي صوت التركيز") || cmd.contains("وقف صوت المطر") || cmd.contains("طفي الضوضاء")) {
            VoiceListenerService.instance?.stopWhiteNoise()
            VoiceListenerService.speakOut("طفيت صوت التركيز.")
            return true
        }

        return false // إذا مو أمر معروف، يرد بأنه ما فهم
    }

    private fun setFlashlight(on: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.setTorchMode(cameraId, on)
            flashlightOn = on
        } catch (_: Exception) {
            // بعض الأجهزة تحتاج صلاحية الكاميرا أو ما تدعم الفلاش، نتجاهل الخطأ بهدوء
        }
    }

    private fun lockDeviceNow() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(comp)) {
            dpm.lockNow()
        } else {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    private fun openAppByName(query: String): Boolean {
        val pm = packageManager
        val packages = pm.getInstalledApplications(0)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val pkg = app.packageName.lowercase()
            if ((label.length > 2 && query.contains(label)) ||
                (query.contains("يوتيوب") && pkg.contains("youtube")) ||
                (query.contains("تليكرام") && pkg.contains("telegram")) ||
                (query.contains("تلجرام") && pkg.contains("telegram")) ||
                (query.contains("انستا") && pkg.contains("instagram")) ||
                (query.contains("واتساب") && pkg.contains("whatsapp")) ||
                (query.contains("تيك توك") && (pkg.contains("musically") || pkg.contains("trill"))) ||
                (query.contains("فيس") && pkg.contains("facebook"))
            ) {
                // إذا گال للصوت افتحه، يسمح بيه لمدة 5 دقائق حتى لو بوقت دراسة
                tempAllowedPackages[app.packageName] = System.currentTimeMillis() + (5 * 60 * 1000L)
                removeLockOverlay()
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    VoiceListenerService.speakOut("فتحتلك $label يا حسين.")
                    return true
                }
            }
        }
        return false
    }

    private fun enterPinAutomatically(pin: String) {
        val root = rootInActiveWindow ?: return
        for (digit in pin) {
            clickNodeByText(root, digit.toString())
        }
        clickNodeByText(root, "OK")
        clickNodeByText(root, "تم")
    }

    private fun clickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
        val list = node.findAccessibilityNodeInfosByText(text)
        for (item in list) {
            if (item.isClickable) {
                item.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            } else if (item.parent?.isClickable == true) {
                item.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }
}
