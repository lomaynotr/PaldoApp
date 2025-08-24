package com.example.paldoapp

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.paldoapp.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // ================================
    // PROPERTIES
    // ================================

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var selectedDateTextView: TextView
    private lateinit var addDataButton: Button
    private var selectedDate: String = ""

    // Simple 3-column table structure
    private val headers = arrayOf("Category", "Kilo", "Amount")
    private val data = arrayOf(
        arrayOf("C1", "", ""),
        arrayOf("C2", "", ""),
        arrayOf("C3", "", ""),
        arrayOf("C4", "", "")
    )

    // Category prices
    private val categoryPrices = mapOf(
        "C1" to 60,
        "C2" to 45,
        "C3" to 35,
        "C4" to 22
    )

    // UI element references
    private val kiloEditTexts = mutableListOf<EditText>()
    private val amountTextViews = mutableListOf<TextView>()
    private lateinit var totalKiloTextView: TextView
    private lateinit var totalAmountTextView: TextView

    // ================================
    // LIFECYCLE METHODS
    // ================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Check if user is authenticated
        if (auth.currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        createDatePicker()
        createTable()
        createButtons()
        setTodayAsDefault()

        // Update counter text with user info
        updateUserInfo()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                showLogoutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ================================
    // UI COMPONENTS
    // ================================

    private fun updateUserInfo() {
        val user = auth.currentUser
        val userInfo = if (user?.isAnonymous == true) {
            "Guest User"
        } else {
            user?.email ?: "Unknown User"
        }
        // binding.counterTextView.text = "Welcome, $userInfo"
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createDatePicker() {
        selectedDateTextView = TextView(this).apply {
            text = "Select Date"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.table_header_bg)
            setOnClickListener { showDatePickerDialog() }
        }
        binding.dateContainer.addView(selectedDateTextView)
    }

    private fun createButtons() {
        addDataButton = Button(this).apply {
            text = "Add Data"
            textSize = 16f
            setPadding(32, 16, 32, 16)
            setOnClickListener { addDataToFirestore() }
        }

        val showDataButton = Button(this).apply {
            text = "Show Data"
            textSize = 16f
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DataActivity::class.java))
            }
        }

        binding.buttonContainer.addView(addDataButton)
        binding.buttonContainer.addView(showDataButton)
    }

    private fun setTodayAsDefault() {
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        selectedDate = dateFormat.format(today.time)
        selectedDateTextView.text = selectedDate
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                selectedDate = dateFormat.format(selectedCalendar.time)
                selectedDateTextView.text = selectedDate
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    // ================================
    // TABLE CREATION
    // ================================

    private fun createTable() {
        createHeaderRow()
        createDataRows()
        createTotalRow()
    }

    private fun createHeaderRow() {
        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.table_header_bg)
        }

        headers.forEach { headerText ->
            headerRow.addView(createHeaderTextView(headerText))
        }
        binding.tableLayout.addView(headerRow)
    }

    private fun createDataRows() {
        data.forEachIndexed { rowIndex, rowData ->
            val row = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundResource(R.drawable.table_row_bg)
            }

            // Category column (read-only)
            row.addView(createDataTextView(rowData[0]))

            // Kilo column (editable)
            val kiloEditText = createKiloEditText(rowData[1], rowIndex)
            kiloEditTexts.add(kiloEditText)
            row.addView(kiloEditText)

            // Amount column (calculated)
            val amountTextView = createDataTextView(rowData[2])
            amountTextViews.add(amountTextView)
            row.addView(amountTextView)

            binding.tableLayout.addView(row)
        }
    }

    private fun createTotalRow() {
        val totalRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.table_header_bg)
        }

        // Empty cell for Category
        totalRow.addView(createHeaderTextView("TOTAL"))

        // Total Kilo
        totalKiloTextView = TextView(this).apply {
            text = "0.0"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
        totalRow.addView(totalKiloTextView)

        // Total Amount
        totalAmountTextView = TextView(this).apply {
            text = "0.0"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
        totalRow.addView(totalAmountTextView)

        binding.tableLayout.addView(totalRow)
    }

    // ================================
    // UI ELEMENT CREATORS
    // ================================

    private fun createHeaderTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun createDataTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(16, 16, 16, 16)
            textSize = 14f
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun createKiloEditText(initialValue: String, rowIndex: Int): EditText {
        return EditText(this).apply {
            setText(initialValue)
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(16, 16, 16, 16)
            textSize = 14f
            background = null
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    handleKiloChange(rowIndex, s.toString())
                }
            })
        }
    }

    // ================================
    // BUSINESS LOGIC
    // ================================

    private fun handleKiloChange(rowIndex: Int, kiloText: String) {
        try {
            val kilo = kiloText.toDoubleOrNull() ?: 0.0
            val category = data[rowIndex][0]
            val price = categoryPrices[category] ?: 0
            val amount = kilo * price

            // Update data array
            data[rowIndex][1] = if (kilo == 0.0) "" else String.format("%.1f", kilo)
            data[rowIndex][2] = if (amount == 0.0) "" else String.format("%.2f", amount)

            // Update amount display
            if (rowIndex < amountTextViews.size) {
                amountTextViews[rowIndex].text = if (amount == 0.0) "" else String.format("%.2f", amount)
            }

            // Update totals
            updateTotals()

        } catch (e: Exception) {
            println("Error updating calculations: ${e.message}")
        }
    }

    private fun updateTotals() {
        try {
            var totalKilo = 0.0
            var totalAmount = 0.0

            // Sum from data array
            data.forEach { row ->
                totalKilo += row[1].toDoubleOrNull() ?: 0.0
                totalAmount += row[2].toDoubleOrNull() ?: 0.0
            }

            // Update total displays
            totalKiloTextView.text = String.format("%.1f", totalKilo)
            totalAmountTextView.text = String.format("%.2f", totalAmount)

        } catch (e: Exception) {
            println("Error updating totals: ${e.message}")
        }
    }

    // ================================
    // FIRESTORE OPERATIONS
    // ================================

    private fun addDataToFirestore() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return
        }

        val totalKilo = totalKiloTextView.text.toString().toDoubleOrNull() ?: 0.0
        val totalAmount = totalAmountTextView.text.toString().toDoubleOrNull() ?: 0.0

        if (totalKilo == 0.0 && totalAmount == 0.0) {
            Toast.makeText(this, "No data to add. Please fill the table first.", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button to prevent multiple clicks
        addDataButton.isEnabled = false
        addDataButton.text = "Saving..."

        // Check if data for this date already exists
        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .whereEqualTo("userId", currentUser.uid)
            .whereEqualTo("date", selectedDate)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // No existing data for this date, create new entry
                    createNewEntry(currentUser.uid, totalKilo, totalAmount)
                } else {
                    // Existing data found, update it by adding to existing values
                    val existingDoc = documents.documents[0]
                    val existingKilo = existingDoc.getDouble("totalKilo") ?: 0.0
                    val existingAmount = existingDoc.getDouble("totalAmount") ?: 0.0

                    // Get existing category values
                    val existingC1 = existingDoc.getDouble("c1") ?: 0.0
                    val existingC2 = existingDoc.getDouble("c2") ?: 0.0
                    val existingC3 = existingDoc.getDouble("c3") ?: 0.0
                    val existingC4 = existingDoc.getDouble("c4") ?: 0.0

                    val newTotalKilo = existingKilo + totalKilo
                    val newTotalAmount = existingAmount + totalAmount

                    // Add current category values to existing ones
                    val newC1 = existingC1 + (data[0][1].toDoubleOrNull() ?: 0.0)
                    val newC2 = existingC2 + (data[1][1].toDoubleOrNull() ?: 0.0)
                    val newC3 = existingC3 + (data[2][1].toDoubleOrNull() ?: 0.0)
                    val newC4 = existingC4 + (data[3][1].toDoubleOrNull() ?: 0.0)

                    updateExistingEntry(existingDoc.id, newTotalKilo, newTotalAmount, newC1, newC2, newC3, newC4)
                }
            }
            .addOnFailureListener { e ->
                addDataButton.isEnabled = true
                addDataButton.text = "Add Data"
                Toast.makeText(this, "Error checking existing data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createNewEntry(userId: String, totalKilo: Double, totalAmount: Double) {
        // Get individual category values
        val c1 = data[0][1].toDoubleOrNull() ?: 0.0
        val c2 = data[1][1].toDoubleOrNull() ?: 0.0
        val c3 = data[2][1].toDoubleOrNull() ?: 0.0
        val c4 = data[3][1].toDoubleOrNull() ?: 0.0

        val dataMap = hashMapOf(
            "userId" to userId,
            "date" to selectedDate,
            "totalKilo" to totalKilo,
            "totalAmount" to totalAmount,
            "c1" to c1,
            "c2" to c2,
            "c3" to c3,
            "c4" to c4,
            "hours" to "", // Initialize empty hours
            "tr" to "T", // Initialize with default T/R value
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .add(dataMap)
            .addOnSuccessListener {
                addDataButton.isEnabled = true
                addDataButton.text = "Add Data"
                Toast.makeText(this, "Data saved successfully!", Toast.LENGTH_SHORT).show()
                clearTableData()
            }
            .addOnFailureListener { e ->
                addDataButton.isEnabled = true
                addDataButton.text = "Add Data"
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateExistingEntry(documentId: String, newTotalKilo: Double, newTotalAmount: Double,
                                    newC1: Double, newC2: Double, newC3: Double, newC4: Double) {
        val updateMap = hashMapOf<String, Any>(
            "totalKilo" to newTotalKilo,
            "totalAmount" to newTotalAmount,
            "c1" to newC1,
            "c2" to newC2,
            "c3" to newC3,
            "c4" to newC4,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .document(documentId)
            .update(updateMap)
            .addOnSuccessListener {
                addDataButton.isEnabled = true
                addDataButton.text = "Add Data"
                Toast.makeText(this, "Data updated successfully! Added to existing entry for $selectedDate", Toast.LENGTH_SHORT).show()
                clearTableData()
            }
            .addOnFailureListener { e ->
                addDataButton.isEnabled = true
                addDataButton.text = "Add Data"
                Toast.makeText(this, "Error updating data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearTableData() {
        // Reset all input fields
        kiloEditTexts.forEach { editText ->
            editText.setText("")
        }

        // Reset data array
        data.forEachIndexed { index, row ->
            row[1] = "" // Kilo
            row[2] = "" // Amount
        }

        // Reset amount text views
        amountTextViews.forEach { textView ->
            textView.text = ""
        }

        // Update totals
        updateTotals()
    }
}