package com.example.easyexchange

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.easyexchange.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val API_KEY = "eb478887e974e140d057bc67" // Free API Key from exchange-rate-api.com
    private val BASE_URL = "https://v6.exchangerate-api.com/"
    
    private val PREFS_NAME = "ExchangePrefs"
    private val KEY_RATES = "cached_rates_"
    
    private var currentRates: Map<String, Double>? = null
    
    private val currencyMap = mapOf(
        R.id.fromInr to "INR", R.id.toInr to "INR",
        R.id.fromUsd to "USD", R.id.toUsd to "USD",
        R.id.fromEur to "EUR", R.id.toEur to "EUR",
        R.id.fromGbp to "GBP", R.id.toGbp to "GBP"
    )

    private val symbolMap = mapOf(
        "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£"
    )

    private val api: ExchangeRateApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        fetchRates()
    }

    private fun setupUI() {
        binding.btnConvert.setOnClickListener { performConversion(showErrors = true) }

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performConversion(showErrors = false)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupFrom.setOnCheckedStateChangeListener { _, _ -> 
            fetchRates() 
        }

        binding.chipGroupTo.setOnCheckedStateChangeListener { _, _ -> 
            performConversion(showErrors = false)
        }
    }

    private fun fetchRates() {
        val fromId = binding.chipGroupFrom.checkedChipId
        val baseCurrency = currencyMap[fromId] ?: "INR"

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.btnConvert.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getLatestRates(API_KEY, baseCurrency)
                }

                if (response.isSuccessful && response.body()?.result == "success") {
                    currentRates = response.body()?.conversionRates
                    saveRatesToCache(baseCurrency, currentRates)
                } else {
                    currentRates = getRatesFromCache(baseCurrency)
                    if (currentRates == null) {
                        currentRates = getDefaultFallbackRates(baseCurrency)
                    }
                    showToast("Using offline rates")
                }
            } catch (e: Exception) {
                currentRates = getRatesFromCache(baseCurrency) ?: getDefaultFallbackRates(baseCurrency)
                showToast("Network error. Using offline rates.")
            } finally {
                binding.loadingIndicator.visibility = View.GONE
                binding.btnConvert.isEnabled = true
                performConversion(showErrors = false)
            }
        }
    }

    private fun performConversion(showErrors: Boolean) {
        val amountStr = binding.etAmount.text.toString().trim()
        val toId = binding.chipGroupTo.checkedChipId
        val targetCurrency = currencyMap[toId] ?: "USD"

        if (amountStr.isEmpty()) {
            if (showErrors) binding.tilAmount.error = "Please enter an amount"
            hideResult()
            return
        }
        binding.tilAmount.error = null

        val amount = amountStr.toDoubleOrNull()
        if (amount == null) {
            if (showErrors) binding.tilAmount.error = "Invalid amount"
            hideResult()
            return
        }

        val rate = currentRates?.get(targetCurrency)
        if (rate != null) {
            val convertedAmount = amount * rate
            updateResultUI(convertedAmount, targetCurrency, rate)
        } else {
            if (showErrors) showToast("Rate not available")
            hideResult()
        }
    }

    private fun updateResultUI(result: Double, toCode: String, rate: Double) {
        val locale = Locale.getDefault()
        val symbol = symbolMap[toCode] ?: ""
        val fromCode = currencyMap[binding.chipGroupFrom.checkedChipId] ?: ""
        
        binding.tvResultValue.text = String.format(locale, "%s %.2f", symbol, result)
        binding.tvResultLabel.text = "Converted Amount ($toCode)"
        binding.tvConversionDetails.text = String.format(locale, "1 %s = %.4f %s", fromCode, rate, toCode)

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
        // Very basic hardcoded fallbacks if everything else fails
        return when (base) {
            "INR" -> mapOf("USD" to 0.012, "EUR" to 0.011, "GBP" to 0.009, "INR" to 1.0)
            "USD" -> mapOf("INR" to 83.5, "EUR" to 0.92, "GBP" to 0.78, "USD" to 1.0)
            else -> mapOf(base to 1.0)
        }
    }
}
