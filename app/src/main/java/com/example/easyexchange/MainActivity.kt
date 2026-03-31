package com.example.easyexchange

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.easyexchange.databinding.ActivityMainBinding
import java.util.Locale

/**
 * MainActivity for the Currency Converter application.
 * This app uses an Options Menu in the Toolbar to trigger currency conversions.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding instance to access UI elements safely
    private lateinit var binding: ActivityMainBinding

    // Realistic static conversion rates (Base: 1 INR)
    private val INR_TO_USD = 0.012
    private val INR_TO_EUR = 0.011
    private val INR_TO_GBP = 0.0094

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the Material Toolbar as the Action Bar
        setSupportActionBar(binding.toolbar)
    }

    /**
     * Inflates the menu resource into the existing menu.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * Handles menu item clicks for currency selection.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_inr_to_usd -> {
                performConversion(getString(R.string.action_inr_to_usd), "$", INR_TO_USD)
                true
            }
            R.id.action_inr_to_eur -> {
                performConversion(getString(R.string.action_inr_to_eur), "€", INR_TO_EUR)
                true
            }
            R.id.action_inr_to_gbp -> {
                performConversion(getString(R.string.action_inr_to_gbp), "£", INR_TO_GBP)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Logic to perform currency conversion.
     * @param targetLabel The label for the conversion (e.g., INR to USD)
     * @param symbol The currency symbol (e.g., $)
     * @param rate The conversion rate from INR
     */
    private fun performConversion(targetLabel: String, symbol: String, rate: Double) {
        val input = binding.etAmount.text.toString().trim()

        if (input.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_input), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val amountInInr = input.toDouble()
            val convertedAmount = amountInInr * rate
            
            // Format result to 2 decimal places
            val formattedResult = String.format(Locale.getDefault(), "%.2f", convertedAmount)
            
            // Update UI
            binding.tvConversionType.text = "Selected: $targetLabel"
            binding.tvResult.text = "Result: $symbol $formattedResult"
            
            // Add a simple fade-in animation for modern feel
            val fadeIn = AlphaAnimation(0.0f, 1.0f)
            fadeIn.duration = 500
            binding.tvResult.startAnimation(fadeIn)

        } catch (e: NumberFormatException) {
            Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show()
        }
    }
}
