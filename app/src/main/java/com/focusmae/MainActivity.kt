package com.focusmae

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.focusmae.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val sound = SoundPlayer()

    // ── Tabata ───────────────────────────────────────────────────────────────

    enum class Phase { IDLE, WORK, REST }

    private var phase      = Phase.IDLE
    private var workMins   = 5
    private var restMins   = 2
    private var totalCycles = 5
    private var currentCycle = 0
    private var remaining  = 0

    private val tabataHandler = Handler(Looper.getMainLooper())
    private val tabataTicker  = object : Runnable {
        override fun run() {
            remaining--
            updateRunningDisplay()
            if (remaining <= 0) advancePhase() else tabataHandler.postDelayed(this, 1000)
        }
    }

    // ── Metronome ────────────────────────────────────────────────────────────

    private var bpm          = 120
    private var metroRunning = false
    private var nextBeatTime = 0L

    // Dedicated background thread — never blocked by UI redraws
    private val metroThread  = HandlerThread("metro-clock").also { it.start() }
    private val metroHandler = Handler(metroThread.looper)
    private val metroTicker  = object : Runnable {
        override fun run() {
            if (!metroRunning) return
            sound.playClick()
            // Schedule next beat at absolute time to prevent cumulative drift
            nextBeatTime += 60000L / bpm
            metroHandler.postAtTime(this, nextBeatTime)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupTabata()
        setupMetronome()
    }

    override fun onDestroy() {
        super.onDestroy()
        tabataHandler.removeCallbacks(tabataTicker)
        metroHandler.removeCallbacks(metroTicker)
        metroThread.quit()
    }

    // ── Tabata setup ──────────────────────────────────────────────────────────

    private fun setupTabata() {
        updateConfigDisplay()
        b.btnWorkMinus.setOnClickListener  { workMins    = (workMins    - 1).coerceAtLeast(1);  updateConfigDisplay() }
        b.btnWorkPlus.setOnClickListener   { workMins    = (workMins    + 1).coerceAtMost(60);  updateConfigDisplay() }
        b.btnRestMinus.setOnClickListener  { restMins    = (restMins    - 1).coerceAtLeast(1);  updateConfigDisplay() }
        b.btnRestPlus.setOnClickListener   { restMins    = (restMins    + 1).coerceAtMost(30);  updateConfigDisplay() }
        b.btnCycMinus.setOnClickListener   { totalCycles = (totalCycles - 1).coerceAtLeast(1);  updateConfigDisplay() }
        b.btnCycPlus.setOnClickListener    { totalCycles = (totalCycles + 1).coerceAtMost(20);  updateConfigDisplay() }
        b.btnTabata.setOnClickListener     { if (phase == Phase.IDLE) startTabata() else stopTabata() }
    }

    private fun updateConfigDisplay() {
        b.tvWorkVal.text   = workMins.toString()
        b.tvRestVal.text   = restMins.toString()
        b.tvCyclesVal.text = totalCycles.toString()
    }

    private fun startTabata() {
        phase        = Phase.WORK
        currentCycle = 1
        remaining    = workMins * 60
        b.configSection.visibility  = View.GONE
        b.runningSection.visibility = View.VISIBLE
        setTabataButton(stop = true)
        sound.playWork()
        updateRunningDisplay()
        tabataHandler.postDelayed(tabataTicker, 1000)
    }

    private fun stopTabata() {
        tabataHandler.removeCallbacks(tabataTicker)
        phase = Phase.IDLE
        b.configSection.visibility  = View.VISIBLE
        b.runningSection.visibility = View.GONE
        setTabataButton(stop = false)
    }

    private fun advancePhase() {
        when (phase) {
            Phase.WORK -> {
                phase     = Phase.REST
                remaining = restMins * 60
                sound.playRest()
                updateRunningDisplay()
                tabataHandler.postDelayed(tabataTicker, 1000)
            }
            Phase.REST -> {
                if (currentCycle >= totalCycles) {
                    sound.playComplete()
                    stopTabata()
                } else {
                    currentCycle++
                    phase     = Phase.WORK
                    remaining = workMins * 60
                    sound.playWork()
                    updateRunningDisplay()
                    tabataHandler.postDelayed(tabataTicker, 1000)
                }
            }
            Phase.IDLE -> {}
        }
    }

    private fun updateRunningDisplay() {
        b.tvCountdown.text = "%02d:%02d".format(remaining / 60, remaining % 60)
        b.tvCycle.text     = "Ciclo $currentCycle / $totalCycles"
        val color = if (phase == Phase.WORK) getColor(R.color.work) else getColor(R.color.rest)
        b.tvPhase.text     = if (phase == Phase.WORK) "TRABAJO" else "DESCANSO"
        b.tvPhase.setTextColor(color)
        b.tvCountdown.setTextColor(color)
    }

    private fun setTabataButton(stop: Boolean) {
        b.btnTabata.text            = if (stop) "DETENER" else "INICIAR"
        b.btnTabata.backgroundTintList = ColorStateList.valueOf(
            getColor(if (stop) R.color.btn_stop else R.color.btn_start)
        )
    }

    // ── Metronome setup ───────────────────────────────────────────────────────

    private fun setupMetronome() {
        b.tvBpm.text = bpm.toString()
        b.btnBpmMinus.setOnClickListener { bpm = (bpm - 1).coerceAtLeast(40);  b.tvBpm.text = bpm.toString() }
        b.btnBpmPlus.setOnClickListener  { bpm = (bpm + 1).coerceAtMost(240); b.tvBpm.text = bpm.toString() }
        b.btnMetro.setOnClickListener    { if (metroRunning) stopMetro() else startMetro() }
    }

    private fun startMetro() {
        metroRunning = true
        setMetroButton(stop = true)
        nextBeatTime = SystemClock.uptimeMillis()
        metroHandler.post(metroTicker)
    }

    private fun stopMetro() {
        metroRunning = false
        metroHandler.removeCallbacks(metroTicker)
        setMetroButton(stop = false)
    }

    private fun setMetroButton(stop: Boolean) {
        b.btnMetro.text            = if (stop) "DETENER" else "INICIAR"
        b.btnMetro.backgroundTintList = ColorStateList.valueOf(
            getColor(if (stop) R.color.btn_stop else R.color.btn_start)
        )
    }
}
