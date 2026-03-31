package com.example.easyexchange

import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.easyexchange.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Conversion rates (Base: 1 INR) - Updated for demonstration
    private val rates = mapOf(
        R.id.chipUsd to Pair(0.012, "USD ($)"),
        R.id.chipEur to Pair(0.011, "EUR (€)"),
        R.id.chipGbp to Pair(0.0092, "GBP (£)"),
        R.id.chipJpy to Pair(1.83, "JPY (¥)"),
        R.id.chipAud to Pair(0.018, "AUD ($)")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Handle "Convert Now" button click
        binding.btnConvert.setOnClickListener {
            performConversion()
        }

        // Instant conversion when a chip is selected
        binding.chipGroupCurrencies.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                if (binding.etAmount.text.toString().isNotEmpty()) {
                    performConversion()
                }
            }
        }
    }

    private fun performConversion() {
        val amountStr = binding.etAmount.text.toString().trim()
        val checkedChipId = binding.chipGroupCurrencies.checkedChipId

        if (amountStr.isEmpty()) {
            binding.tilAmount.error = getString(R.string.error_empty_input)
            return
        } else {
            binding.tilAmount.error = null
        }

        if (checkedChipId == View.NO_ID) {
            Toast.makeText(this, getString(R.string.error_select_currency), Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null) {
            binding.tilAmount.error = getString(R.string.error_invalid_input)
            return
        }

        val rateInfo = rates[checkedChipId]
        if (rateInfo != null) {
            val (rate, label) = rateInfo
            val result = amount * rate
            
            showResult(result, label, rate)
        }
    }

    private fun showResult(result: Double, label: String, rate: Double) {
        // Update UI text
        binding.tvResultValue.text = String.format(Locale.getDefault(), "%.2f", result)
        binding.tvResultLabel.text = "Converted Amount ($label)"
        binding.tvConversionDetails.text = "1 INR = $rate ${label.substringBefore(" ")}"

        // Animate Result Card appearance if it was hidden
        if (binding.cardResult.visibility != View.VISIBLE) {
            binding.cardResult.visibility = View.VISIBLE
            val fadeIn = AlphaAnimation(0f, 1f)
            fadeIn.duration = 400
            binding.cardResult.startAnimation(fadeIn)
        } else {
            // Subtle pop animation for update
            val scaleUp = AlphaAnimation(0.5f, 1f)
            scaleUp.duration = 200
            binding.cardResult.startAnimation(scaleUp)
        }
    }
}
