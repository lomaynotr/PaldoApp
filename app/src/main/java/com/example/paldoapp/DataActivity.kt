package com.example.paldoapp

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.paldoapp.databinding.ActivityDataBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import android.view.View
import android.widget.PopupMenu

// Define CounterData class with more fields
data class CounterData(
    val userId: String = "",
    val date: String = "",
    val totalKilo: Double = 0.0,
    val totalAmount: Double = 0.0,
    val c1: Double = 0.0,
    val c2: Double = 0.0,
    val c3: Double = 0.0,
    val c4: Double = 0.0,
    val hours: String = "",
    val tr: String = "T",
    val timestamp: Long = 0L,
    val documentId: String = "" // Add document ID to track records
)

class DataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBinding
    private val headers = arrayOf("Date", "Total Kilo", "Total Amount", "Action") // Add Action column
    private var allDataList = mutableListOf<CounterData>()
    private var filteredDataList = mutableListOf<CounterData>()
    private var selectedMonth = ""
    private var availableMonths = mutableListOf<String>()
    private var monthButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        createTable()
        loadDataFromFirestore()

        // Set up print summary button
        binding.showSummaryButton.setOnClickListener {
            val intent = Intent(this, PrintActivity::class.java)
            startActivity(intent)
        }
        val optionBtn = findViewById<View>(R.id.option_btn)
        optionBtn?.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    private fun createMonthSelector() {
        // Clear existing month buttons
        binding.monthContainer.removeAllViews()
        monthButtons.clear()

        // Create "All" button
        val allButton = createMonthButton("All")
        binding.monthContainer.addView(allButton)
        monthButtons.add(allButton)

        // Create buttons for each available month
        availableMonths.forEach { month ->
            val button = createMonthButton(month)
            binding.monthContainer.addView(button)
            monthButtons.add(button)
        }

        // Select "All" by default if no month is selected
        if (selectedMonth.isEmpty()) {
            selectMonth("All")
        }
    }

    private fun createMonthButton(month: String): Button {
        return Button(this).apply {
            text = month
            textSize = 14f
            setPadding(24, 12, 24, 12)
            setOnClickListener { selectMonth(month) }

            // Set margin
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            layoutParams = params
        }
    }

    private fun selectMonth(month: String) {
        selectedMonth = month

        // Update button states
        monthButtons.forEach { button ->
            if (button.text == month) {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.LTGRAY)
                button.setTextColor(Color.BLACK)
            }
        }

        // Filter data based on selected month
        filterDataByMonth(month)

        // Sort the filtered data by date
        sortDataByDate()

        updateTable()
        updateTotals()
    }

    private fun sortDataByDate() {
        // Custom comparator that compares dates
        val dateComparator = Comparator<CounterData> { data1, data2 ->
            try {
                val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                val date1 = dateFormat.parse(data1.date)
                val date2 = dateFormat.parse(data2.date)
                date1?.compareTo(date2 ?: Date()) ?: 0
            } catch (e: Exception) {
                // Fallback to string comparison if date parsing fails
                data1.date.compareTo(data2.date)
            }
        }

        // Sort both lists
        allDataList.sortWith(dateComparator)
        filteredDataList.sortWith(dateComparator)
    }

    private fun filterDataByMonth(month: String) {
        filteredDataList.clear()

        if (month == "All") {
            filteredDataList.addAll(allDataList)
        } else {
            filteredDataList.addAll(allDataList.filter { data ->
                getMonthYearFromDate(data.date) == month
            })
        }
    }

    private fun getMonthYearFromDate(dateString: String): String {
        return try {
            // Handle the new date format "August 1, 2025"
            val inputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            // Fallback for old format "dd/MM/yyyy" if any old data exists
            try {
                val fallbackFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                val date = fallbackFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e2: Exception) {
                "Unknown"
            }
        }
    }

    private fun extractAvailableMonths() {
        val monthsSet = mutableSetOf<String>()

        allDataList.forEach { data ->
            val monthYear = getMonthYearFromDate(data.date)
            monthsSet.add(monthYear)
        }

        // Sort months chronologically (oldest first)
        availableMonths = monthsSet.sortedWith { month1, month2 ->
            try {
                val format = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                val date1 = format.parse(month1)
                val date2 = format.parse(month2)
                date1?.compareTo(date2 ?: Date()) ?: 0
            } catch (e: Exception) {
                month1.compareTo(month2)
            }
        }.toMutableList()
    }

    private fun createTable() {
        createHeaderRow()
    }

    private fun createHeaderRow() {
        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@DataActivity, R.drawable.table_header_bg)
        }

        headers.forEach { headerText ->
            headerRow.addView(createHeaderTextView(headerText))
        }
        binding.dataTableLayout.addView(headerRow)
    }

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

    private fun createDeleteButton(data: CounterData): ImageButton {
        return ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.5f)

            setOnClickListener {
                showDeleteConfirmationDialog(data)
            }
        }
    }

    private fun createEditButton(data: CounterData): ImageButton {
        return ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(8, 8, 8, 8)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.5f)

            setOnClickListener {
                showEditDialog(data)
            }
        }
    }

    private fun showDeleteConfirmationDialog(data: CounterData) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete the entry for ${data.date}?\n\nKilo: ${String.format("%.1f", data.totalKilo)}\nAmount: ${String.format("%.2f", data.totalAmount)}")
            .setPositiveButton("Delete") { _, _ ->
                deleteDataFromFirestore(data)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(data: CounterData) {
        try {
            // Create a dialog for editing
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_data, null)
            val alertDialog = AlertDialog.Builder(this)
                .setTitle("Edit Entry")
                .setView(dialogView)
                .create()

            // Find views in the dialog
            val editDate = dialogView.findViewById<TextView>(R.id.edit_date)
            val editTotalKilo = dialogView.findViewById<EditText>(R.id.edit_total_kilo)
            val editC1 = dialogView.findViewById<EditText>(R.id.edit_c1)
            val editC2 = dialogView.findViewById<EditText>(R.id.edit_c2)
            val editC3 = dialogView.findViewById<EditText>(R.id.edit_c3)
            val editC4 = dialogView.findViewById<EditText>(R.id.edit_c4)
            val editHours = dialogView.findViewById<EditText>(R.id.edit_hours)
            val editTR = dialogView.findViewById<Spinner>(R.id.edit_tr)

            // Set up the spinner
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("T", "R"))
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            editTR.adapter = adapter

            // Get the current values from Firestore
            FirebaseFirestore.getInstance()
                .collection("counter_data")
                .document(data.documentId)
                .get()
                .addOnSuccessListener { document ->
                    // Set current values in the dialog
                    editDate.text = data.date
                    editTotalKilo.setText(data.totalKilo.toString())

                    // Set category values if they exist
                    editC1.setText(document.getDouble("c1")?.toString() ?: "0.0")
                    editC2.setText(document.getDouble("c2")?.toString() ?: "0.0")
                    editC3.setText(document.getDouble("c3")?.toString() ?: "0.0")
                    editC4.setText(document.getDouble("c4")?.toString() ?: "0.0")

                    // Set hours if it exists
                    editHours.setText(document.getString("hours") ?: "")

                    // Set T/R selection
                    val trValue = document.getString("tr") ?: "T"
                    editTR.setSelection(if (trValue == "T") 0 else 1)

                    // Add save and cancel buttons
                    dialogView.findViewById<Button>(R.id.btn_save).setOnClickListener {
                        saveEditedData(
                            data.documentId,
                            editTotalKilo.text.toString().toDoubleOrNull() ?: 0.0,
                            editC1.text.toString().toDoubleOrNull() ?: 0.0,
                            editC2.text.toString().toDoubleOrNull() ?: 0.0,
                            editC3.text.toString().toDoubleOrNull() ?: 0.0,
                            editC4.text.toString().toDoubleOrNull() ?: 0.0,
                            editHours.text.toString(),
                            editTR.selectedItem.toString()
                        )
                        alertDialog.dismiss()
                    }

                    dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
                        alertDialog.dismiss()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                    alertDialog.dismiss()
                }

            alertDialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error showing dialog: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun saveEditedData(
        documentId: String,
        totalKilo: Double,
        c1: Double,
        c2: Double,
        c3: Double,
        c4: Double,
        hours: String,
        tr: String
    ) {
        // Calculate total amount based on category prices
        val categoryPrices = mapOf(
            "C1" to 60,
            "C2" to 45,
            "C3" to 35,
            "C4" to 22
        )

        // Calculate new total amount based on categories
        val totalAmount = (c1 * (categoryPrices["C1"] ?: 0)) +
                (c2 * (categoryPrices["C2"] ?: 0)) +
                (c3 * (categoryPrices["C3"] ?: 0)) +
                (c4 * (categoryPrices["C4"] ?: 0))

        // Calculate hours(ft)
        val hoursValue = hours.toDoubleOrNull() ?: 0.0
        val hoursFt = hoursValue * 1235

        // Calculate total amount including hours
        val finalAmount = totalAmount + hoursFt

        // Update data in Firestore
        val updates = hashMapOf<String, Any>(
            "totalKilo" to totalKilo,
            "totalAmount" to totalAmount,
            "c1" to c1,
            "c2" to c2,
            "c3" to c3,
            "c4" to c4,
            "hours" to hours,
            "tr" to tr,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .document(documentId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Data updated successfully!", Toast.LENGTH_SHORT).show()

                // Reload data to reflect changes
                loadDataFromFirestore()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteDataFromFirestore(data: CounterData) {
        if (data.documentId.isEmpty()) {
            Toast.makeText(this, "Cannot delete: Document ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .document(data.documentId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Entry deleted successfully", Toast.LENGTH_SHORT).show()
                // Remove from local lists
                allDataList.removeAll { it.documentId == data.documentId }
                filteredDataList.removeAll { it.documentId == data.documentId }

                // Update the UI
                extractAvailableMonths()
                createMonthSelector()
                updateTable()
                updateTotals()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting entry: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDataFromFirestore() {
        // Check if user is logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                allDataList.clear()

                for (document in documents) {
                    try {
                        // Manually create CounterData from document data to handle missing fields
                        val data = CounterData(
                            userId = document.getString("userId") ?: "",
                            date = document.getString("date") ?: "",
                            totalKilo = document.getDouble("totalKilo") ?: 0.0,
                            totalAmount = document.getDouble("totalAmount") ?: 0.0,
                            c1 = document.getDouble("c1") ?: 0.0,
                            c2 = document.getDouble("c2") ?: 0.0,
                            c3 = document.getDouble("c3") ?: 0.0,
                            c4 = document.getDouble("c4") ?: 0.0,
                            hours = document.getString("hours") ?: "",
                            tr = document.getString("tr") ?: "T",
                            timestamp = document.getLong("timestamp") ?: 0L,
                            documentId = document.id
                        )
                        allDataList.add(data)
                    } catch (e: Exception) {
                        // Skip malformed documents
                        println("Error parsing document ${document.id}: ${e.message}")
                    }
                }

                // Extract available months and create month selector
                extractAvailableMonths()
                createMonthSelector()

                // Sort the data by date (oldest to latest)
                sortDataByDate()

                Toast.makeText(this, "Loaded ${allDataList.size} entries", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                println("Firestore error: ${e.localizedMessage}")
            }
    }

    private fun updateTable() {
        // Remove all rows except header
        val childCount = binding.dataTableLayout.childCount
        if (childCount > 1) {
            binding.dataTableLayout.removeViews(1, childCount - 1)
        }

        // Add data rows from filtered list
        filteredDataList.forEach { data ->
            // Determine if the day is Sunday
            val isSunday = isDaySunday(data.date)

            val row = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                // Apply yellow background for Sundays
                if (isSunday) {
                    setBackgroundResource(R.drawable.table_row_sunday_bg)
                } else {
                    setBackgroundResource(R.drawable.table_row_bg)
                }
            }

            // Date column
            row.addView(createDataTextView(data.date))

            // Total Kilo column
            row.addView(createDataTextView(String.format("%.1f", data.totalKilo)))

            // Total Amount column - Format with thousands separator
            row.addView(createDataTextView(String.format("%,d", data.totalAmount.toInt())))

            // Action column - contains both Edit and Delete buttons
            val actionContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER
            }

            actionContainer.addView(createEditButton(data))
            actionContainer.addView(createDeleteButton(data))

            row.addView(actionContainer)

            binding.dataTableLayout.addView(row)
        }
    }

    // Helper function to determine if a date is Sunday
    private fun isDaySunday(dateString: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val date = dateFormat.parse(dateString)
            val calendar = Calendar.getInstance()
            calendar.time = date ?: return false
            calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        } catch (e: Exception) {
            false
        }
    }

    private fun updateTotals() {
        var totalKilo = 0.0
        var totalAmount = 0.0
        val totalEntries = filteredDataList.size // Count of entries

        filteredDataList.forEach { data ->
            totalKilo += data.totalKilo
            totalAmount += data.totalAmount
        }

        // Create totals row
        val totalRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@DataActivity, R.drawable.table_header_bg)
        }

        // "TOTAL" label
        totalRow.addView(createTotalTextView("TOTAL"))

        // Total Kilo
        totalRow.addView(createTotalTextView(String.format("%.1f", totalKilo)))

        // Total Amount - Format with thousands separator
        totalRow.addView(createTotalTextView(String.format("%,d", totalAmount.toInt())))

        // Total days/entries in the Action column
        val daysText = if (totalEntries == 1) "1 day" else "$totalEntries days"
        totalRow.addView(createTotalTextView(daysText))

        binding.dataTableLayout.addView(totalRow)
    }

    private fun createTotalTextView(text: String): TextView {
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

    // Function to check if data exists for a date (call from MainActivity)
    fun checkDateExists(date: String, callback: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            callback(false)
            return
        }

        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .whereEqualTo("userId", currentUser.uid)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { documents ->
                callback(!documents.isEmpty)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    // Method to handle show summary button click
    fun showSummary(view: android.view.View) {
        val intent = Intent(this, PrintActivity::class.java)
        startActivity(intent)
    }


// And add this method to your DataActivity class:

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_popup, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_enter -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.menu_data -> {
                    // Already in DataActivity, do nothing or refresh
                    true
                }
                R.id.menu_summary -> {
                    startActivity(Intent(this, PrintActivity::class.java))
                    true
                }
                R.id.menu_logout -> {
                    showLogoutDialog()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}