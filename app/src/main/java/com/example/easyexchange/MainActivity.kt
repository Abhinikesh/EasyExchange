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
 * MainActivity for EasyExchange - A professional Currency Converter.
 * Implements real-time conversion logic with Material 3 UI components.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Realistic static conversion rates (Base: 1 INR)
    private val rates = mapOf(
        R.id.chipUsd to RateInfo(0.012, "USD", "$"),
        R.id.chipEur to RateInfo(0.011, "EUR", "€"),
        R.id.chipGbp to RateInfo(0.0095, "GBP", "£"),
        R.id.chipJpy to RateInfo(1.83, "JPY", "¥"),
        R.id.chipAud to RateInfo(0.018, "AUD", "$")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Handle "Convert Now" button click (for explicit action)
        binding.btnConvert.setOnClickListener {
            performConversion(showErrors = true)
        }

        // Real-time conversion when amount changes
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performConversion(showErrors = false)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Instant conversion when a currency chip is selected
        binding.chipGroupCurrencies.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                performConversion(showErrors = false)
            }
        }
    }

    /**
     * Core conversion logic.
     * @param showErrors If true, show error messages (used for button click).
     */
    private fun performConversion(showErrors: Boolean) {
        val amountStr = binding.etAmount.text.toString().trim()
        val checkedChipId = binding.chipGroupCurrencies.checkedChipId

        // Basic validation
        if (amountStr.isEmpty()) {
            if (showErrors) binding.tilAmount.error = getString(R.string.error_empty_input)
            hideResult()
            return
        } else {
            binding.tilAmount.error = null
        }

        // Check if a currency is selected
        if (checkedChipId == View.NO_ID) {
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

        // Perform calculation
        val rateInfo = rates[checkedChipId]
        if (rateInfo != null) {
            val convertedAmount = amount * rateInfo.rate
            updateResultUI(amount, convertedAmount, rateInfo)
        }
    }

    /**
     * Updates the Result Card with formatted values and animations.
     */
    private fun updateResultUI(inputAmount: Double, result: Double, rateInfo: RateInfo) {
        val locale = Locale.getDefault()
        
        // Format values
        val formattedResult = String.format(locale, "%.2f", result)
        val formattedRate = String.format(locale, "%.4f", rateInfo.rate)

        // Update UI
        binding.tvResultValue.text = getString(R.string.amount_hint).replace("0.00", "${rateInfo.symbol} $formattedResult")
        binding.tvResultLabel.text = getString(R.string.result_label_formatted, rateInfo.code)
        binding.tvConversionDetails.text = getString(R.string.conversion_details_formatted, formattedRate, rateInfo.code)

        // Show card with animation
        if (binding.cardResult.visibility != View.VISIBLE) {
            binding.cardResult.visibility = View.VISIBLE
            val fadeIn = AlphaAnimation(0f, 1f)
            fadeIn.duration = 400
            binding.cardResult.startAnimation(fadeIn)
        } else {
            // Pulse animation for value update
            val pulse = ScaleAnimation(1f, 1.05f, 1f, 1.05f, 
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f)
            pulse.duration = 150
            pulse.repeatMode = ScaleAnimation.REVERSE
            pulse.repeatCount = 1
            binding.cardResult.startAnimation(pulse)
        }
    }

    private fun hideResult() {
        binding.cardResult.visibility = View.INVISIBLE
    }

    /**
     * Data class to hold currency information.
     */
    data class RateInfo(val rate: Double, val code: String, val symbol: String)
}
