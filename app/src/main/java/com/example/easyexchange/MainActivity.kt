package com.example.easyexchange

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.easyexchange.databinding.ActivityMainBinding
import java.util.Locale

/**
 * MainActivity for EasyExchange - A professional Bidirectional Currency Converter.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Conversion rates relative to 1 INR (Base)
    private val currencyMap = mapOf(
        R.id.fromInr to CurrencyInfo("INR", "₹", 1.0),
        R.id.toInr to CurrencyInfo("INR", "₹", 1.0),
        R.id.fromUsd to CurrencyInfo("USD", "$", 0.012),
        R.id.toUsd to CurrencyInfo("USD", "$", 0.012),
        R.id.fromEur to CurrencyInfo("EUR", "€", 0.011),
        R.id.toEur to CurrencyInfo("EUR", "€", 0.011),
        R.id.fromGbp to CurrencyInfo("GBP", "£", 0.0095),
        R.id.toGbp to CurrencyInfo("GBP", "£", 0.0095)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.btnConvert.setOnClickListener {
            performConversion(showErrors = true)
        }

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performConversion(showErrors = false)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupFrom.setOnCheckedStateChangeListener { _, _ ->
            performConversion(showErrors = false)
        }

        binding.chipGroupTo.setOnCheckedStateChangeListener { _, _ ->
            performConversion(showErrors = false)
        }
    }

    private fun performConversion(showErrors: Boolean) {
        val amountStr = binding.etAmount.text.toString().trim()
        val fromId = binding.chipGroupFrom.checkedChipId
        val toId = binding.chipGroupTo.checkedChipId

        if (amountStr.isEmpty()) {
            if (showErrors) binding.tilAmount.error = getString(R.string.error_empty_input)
            hideResult()
            return
        } else {
            binding.tilAmount.error = null
        }

        if (fromId == View.NO_ID || toId == View.NO_ID) {
            if (showErrors) Toast.makeText(this, getString(R.string.error_select_currency), Toast.LENGTH_SHORT).show()
            hideResult()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null) {
            if (showErrors) binding.tilAmount.error = getString(R.string.error_invalid_input)
            hideResult()
            return
        }

        val fromCurrency = currencyMap[fromId]
        val toCurrency = currencyMap[toId]

        if (fromCurrency != null && toCurrency != null) {
            // formula: (amount / fromRate) * toRate
            // since rates are relative to INR (1 INR = X USD)
            // Example: 100 USD to INR -> (100 / 0.012) * 1.0 = 8333.33
            val convertedAmount = (amount / fromCurrency.rateToInr) * toCurrency.rateToInr
            val exchangeRate = toCurrency.rateToInr / fromCurrency.rateToInr
            
            updateResultUI(convertedAmount, toCurrency, exchangeRate, fromCurrency.code)
        }
    }

    private fun updateResultUI(result: Double, toCurrency: CurrencyInfo, exchangeRate: Double, fromCode: String) {
        val locale = Locale.getDefault()
        val formattedResult = String.format(locale, "%.2f", result)
        val formattedRate = String.format(locale, "%.4f", exchangeRate)

        binding.tvResultValue.text = "${toCurrency.symbol} $formattedResult"
        binding.tvResultLabel.text = getString(R.string.result_label_formatted, toCurrency.code)
        binding.tvConversionDetails.text = getString(R.string.conversion_details_formatted, fromCode, formattedRate, toCurrency.code)

        if (binding.cardResult.visibility != View.VISIBLE) {
            binding.cardResult.visibility = View.VISIBLE
            val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 400 }
            binding.cardResult.startAnimation(fadeIn)
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

    private fun hideResult() {
        binding.cardResult.visibility = View.INVISIBLE
    }

    data class CurrencyInfo(val code: String, val symbol: String, val rateToInr: Double)
}
