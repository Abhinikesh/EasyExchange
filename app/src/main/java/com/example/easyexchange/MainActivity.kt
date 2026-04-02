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
    private val API_KEY = "eb478887e974e140d057bc67"
    private val BASE_URL = "https://v6.exchangerate-api.com/"
    
    private val PREFS_NAME = "ExchangePrefs"
    private val KEY_RATES = "cached_rates_"
    
    private var currentRates: Map<String, Double>? = null
    
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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        fetchRates()
    }

    private fun setupUI() {
        // Setup Dropdowns
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        binding.autoCompleteFrom.setAdapter(adapter)
        binding.autoCompleteTo.setAdapter(adapter)

        // Set Defaults
        binding.autoCompleteFrom.setText("USD", false)
        binding.autoCompleteTo.setText("INR", false)

        // Listeners
        binding.autoCompleteFrom.setOnItemClickListener { _, _, _, _ -> fetchRates() }
        binding.autoCompleteTo.setOnItemClickListener { _, _, _, _ -> performConversion(false) }

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performConversion(false)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSwap.setOnClickListener { swapCurrencies() }
        
        binding.btnConvert.setOnClickListener { performConversion(true) }
    }

    private fun swapCurrencies() {
        val fromText = binding.autoCompleteFrom.text.toString()
        val toText = binding.autoCompleteTo.text.toString()

        // Animation for Swap FAB
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
                    showToast("Using offline rates")
                }
            } catch (e: Exception) {
                currentRates = getRatesFromCache(baseCurrency) ?: getDefaultFallbackRates(baseCurrency)
                showToast("Network error. Using offline rates.")
            } finally {
                binding.loadingIndicator.visibility = View.GONE
                performConversion(false)
            }
        }
    }

    private fun performConversion(showErrors: Boolean) {
        val amountStr = binding.etAmount.text.toString().trim()
        val targetCurrency = binding.autoCompleteTo.text.toString()

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
            hideResult()
        }
    }

    private fun updateResultUI(result: Double, toCode: String, rate: Double) {
        val locale = Locale.getDefault()
        val symbol = symbolMap[toCode] ?: ""
        val fromCode = binding.autoCompleteFrom.text.toString()
        
        binding.tvResultValue.text = String.format(locale, "%s %,.2f", symbol, result)
        binding.tvResultLabel.text = "Result in $toCode"
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
            "INR" -> mapOf("USD" to 0.012, "EUR" to 0.011, "GBP" to 0.009, "INR" to 1.0)
            "USD" -> mapOf("INR" to 83.5, "EUR" to 0.92, "GBP" to 0.78, "USD" to 1.0)
            else -> mapOf(base to 1.0)
        }
    }
}
