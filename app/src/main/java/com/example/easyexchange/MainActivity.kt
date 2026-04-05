package com.example.easyexchange

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.easyexchange.databinding.ActivityMainBinding
import com.example.easyexchange.network.ExchangeRateApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val API_KEY = "eb478887e974e140d057bc67"
    private val BASE_URL = "https://v6.exchangerate-api.com/"
    
    private val PREFS_NAME = "ExchangePrefs"
    private val KEY_RATES = "cached_rates_"
    private val KEY_HISTORY = "conversion_history"
    
    private var currentRates: Map<String, Double>? = null
    private var conversionJob: Job? = null
    private var isSplashScreenVisible = true
    
    private val currencies = arrayOf("INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD")
    private val symbolMap = mapOf(
        "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£", 
        "JPY" to "¥", "AUD" to "A$", "CAD" to "C$"
    )

    private val api: ExchangeRateApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { isSplashScreenVisible }
        
        lifecycleScope.launch {
            delay(1000)
            isSplashScreenVisible = false
        }
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        fetchRates()
    }

    private fun setupUI() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        binding.autoCompleteFrom.setAdapter(adapter)
        binding.autoCompleteTo.setAdapter(adapter)

        binding.autoCompleteFrom.setText("USD", false)
        binding.autoCompleteTo.setText("INR", false)

        binding.autoCompleteFrom.setOnItemClickListener { _, _, _, _ -> fetchRates() }
        binding.autoCompleteTo.setOnItemClickListener { _, _, _, _ -> performConversion(false) }

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                conversionJob?.cancel()
                conversionJob = lifecycleScope.launch {
                    delay(300)
                    performConversion(false)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSwap.setOnClickListener { swapCurrencies() }
        binding.btnClear.setOnClickListener { resetInputs() }
        binding.btnHistory.setOnClickListener { showHistory() }
    }

    private fun resetInputs() {
        binding.etAmount.setText("")
        binding.tilAmount.error = null
        hideResult()
        showToast(getString(R.string.inputs_cleared))
    }

    private fun swapCurrencies() {
        val fromText = binding.autoCompleteFrom.text.toString()
        val toText = binding.autoCompleteTo.text.toString()

        val rotate = RotateAnimation(0f, 180f, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f)
        rotate.duration = 300
        binding.btnSwap.startAnimation(rotate)

        binding.autoCompleteFrom.setText(toText, false)
        binding.autoCompleteTo.setText(fromText, false)

        fetchRates()
    }

    private fun fetchRates() {
        val baseCurrency = binding.autoCompleteFrom.text.toString()
        binding.loadingIndicator.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getLatestRates(API_KEY, baseCurrency)
                }

                if (response.isSuccessful && response.body()?.result == "success") {
                    currentRates = response.body()?.conversionRates
                    saveRatesToCache(baseCurrency, currentRates)
                } else {
                    currentRates = getRatesFromCache(baseCurrency) ?: getDefaultFallbackRates(baseCurrency)
                    showToast(getString(R.string.offline_mode_cached))
                }
            } catch (e: Exception) {
                currentRates = getRatesFromCache(baseCurrency) ?: getDefaultFallbackRates(baseCurrency)
                showToast(getString(R.string.offline_mode_network))
            } finally {
                binding.loadingIndicator.visibility = View.GONE
                performConversion(false)
            }
        }
    }

    private fun performConversion(showErrors: Boolean) {
        val amountStr = binding.etAmount.text.toString().trim()
        val fromCurrency = binding.autoCompleteFrom.text.toString()
        val targetCurrency = binding.autoCompleteTo.text.toString()

        if (amountStr.isEmpty()) {
            if (showErrors) binding.tilAmount.error = getString(R.string.error_empty_input)
            hideResult()
            return
        }
        binding.tilAmount.error = null

        val amount = amountStr.toDoubleOrNull()
        if (amount == null) {
            if (showErrors) binding.tilAmount.error = getString(R.string.error_invalid_input)
            hideResult()
            return
        }

        val rate = currentRates?.get(targetCurrency)
        if (rate != null) {
            val convertedAmount = amount * rate
            updateResultUI(convertedAmount, targetCurrency, rate)
            saveToHistory(amount, fromCurrency, targetCurrency, convertedAmount)
        } else {
            hideResult()
        }
    }

    private fun updateResultUI(result: Double, toCode: String, rate: Double) {
        val locale = Locale.getDefault()
        val symbol = symbolMap[toCode] ?: ""
        val fromCode = binding.autoCompleteFrom.text.toString()
        
        binding.tvResultValue.text = String.format(locale, "%s %,.2f", symbol, result)
        binding.tvResultLabel.text = getString(R.string.result_label_template, toCode)
        binding.tvConversionDetails.text = getString(R.string.conversion_details_formatted, fromCode, String.format(locale, "%.4f", rate), toCode)

        if (binding.cardResult.visibility != View.VISIBLE) {
            binding.cardResult.visibility = View.VISIBLE
            binding.cardResult.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 400 })
        } else {
            val pulse = ScaleAnimation(1f, 1.02f, 1f, 1.02f, 
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 150
                repeatMode = ScaleAnimation.REVERSE
                repeatCount = 1
            }
            binding.cardResult.startAnimation(pulse)
        }
    }

    private fun hideResult() { binding.cardResult.visibility = View.INVISIBLE }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // --- History Implementation ---

    data class HistoryItem(val from: String, val to: String, val amount: Double, val result: Double, val timestamp: Long = System.currentTimeMillis())

    private fun saveToHistory(amount: Double, from: String, to: String, result: Double) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyJson = prefs.getString(KEY_HISTORY, "[]")
        val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
        val history: MutableList<HistoryItem> = Gson().fromJson(historyJson, type)
        
        if (history.isNotEmpty()) {
            val last = history.first()
            if (last.from == from && last.to == to && Math.abs(last.amount - amount) < 0.001) return
        }

        history.add(0, HistoryItem(from, to, amount, result))
        if (history.size > 20) history.removeAt(history.size - 1)
        
        prefs.edit().putString(KEY_HISTORY, Gson().toJson(history)).apply()
    }

    private fun showHistory() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyJson = prefs.getString(KEY_HISTORY, "[]")
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        val history: List<HistoryItem> = Gson().fromJson(historyJson, type)

        if (history.isEmpty()) {
            showToast(getString(R.string.history_empty))
            return
        }

        val historyText = history.joinToString("\n\n") { 
            val fromSymbol = symbolMap[it.from] ?: ""
            val toSymbol = symbolMap[it.to] ?: ""
            String.format(Locale.getDefault(), "%s %,.2f %s \u2192 %s %,.2f %s", fromSymbol, it.amount, it.from, toSymbol, it.result, it.to)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.history_title))
            .setMessage(historyText)
            .setPositiveButton(getString(R.string.close_button), null)
            .setNeutralButton(getString(R.string.history_clear_all)) { _, _ -> 
                prefs.edit().remove(KEY_HISTORY).apply()
                showToast(getString(R.string.history_cleared))
            }
            .show()
    }

    // --- Caching & Fallbacks ---

    private fun saveRatesToCache(base: String, rates: Map<String, Double>?) {
        if (rates == null) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(rates)
        prefs.edit().putString(KEY_RATES + base, json).apply()
    }

    private fun getRatesFromCache(base: String): Map<String, Double>? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RATES + base, null) ?: return null
        val type = object : TypeToken<Map<String, Double>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun getDefaultFallbackRates(base: String): Map<String, Double> {
        return when (base) {
            "INR" -> mapOf("USD" to 0.012, "EUR" to 0.011, "GBP" to 0.009, "JPY" to 1.81, "AUD" to 0.018, "CAD" to 0.016, "INR" to 1.0)
            "USD" -> mapOf("INR" to 83.5, "EUR" to 0.92, "GBP" to 0.78, "JPY" to 151.0, "AUD" to 1.52, "CAD" to 1.36, "USD" to 1.0)
            "EUR" -> mapOf("INR" to 90.5, "USD" to 1.08, "GBP" to 0.85, "JPY" to 164.0, "AUD" to 1.65, "CAD" to 1.48, "EUR" to 1.0)
            else -> mapOf(base to 1.0)
        }
    }
}
