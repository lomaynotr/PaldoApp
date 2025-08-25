package com.example.paldoapp

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paldoapp.databinding.ActivityPrintBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import jxl.Workbook
import jxl.format.Colour
import jxl.write.Label
import jxl.write.Number
import jxl.write.WritableCellFormat
import jxl.write.WritableFont
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Data class to hold print data with additional fields
data class PrintData(
    val date: String = "",
    val day: String = "",
    val totalKilo: Double = 0.0,
    val c1: Double = 0.0,
    val c2: Double = 0.0,
    val c3: Double = 0.0,
    val c4: Double = 0.0,
    val kiloFt: Double = 0.0,
    val hours: String = "",
    val hoursFt: Double = 0.0,
    val tr: String = "",
    val amount: Double = 0.0,
    val documentId: String = ""
)

class PrintActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrintBinding
    private val headers = arrayOf("Date", "Day", "Total", "C1", "C2", "C3", "C4", "Kilo(ft)", "Hours", "Hours(ft)", "T/R", "Amount")
    private var printDataList = mutableListOf<PrintData>()
    private val trOptions = arrayOf("", "T", "R") // Updated with empty option
    private var currentMonth = Calendar.getInstance().get(Calendar.MONTH)
    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private val HOUR_MULTIPLIER = 1235

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createTable()
        prePopulateMonthDays()
        loadDataFromFirestore()

        binding.btnSaveChanges.setOnClickListener {
            saveChangesToFirestore()
        }

        // Add Export to Excel button listener
        binding.btnExportExcel.setOnClickListener {
            exportToExcel()
        }
        val optionBtn = findViewById<View>(R.id.option_btn)
        optionBtn?.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    private fun prePopulateMonthDays() {
        val calendar = Calendar.getInstance()
        calendar.set(currentYear, currentMonth, 1)

        // Get the number of days in the current month
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Clear existing data
        printDataList.clear()

        // Create entries for each day of the month
        for (day in 1..daysInMonth) {
            calendar.set(currentYear, currentMonth, day)

            val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

            val formattedDate = dateFormat.format(calendar.time)
            val dayOfWeek = dayFormat.format(calendar.time)

            val printData = PrintData(
                date = formattedDate,
                day = dayOfWeek,
                totalKilo = 0.0,
                c1 = 0.0,
                c2 = 0.0,
                c3 = 0.0,
                c4 = 0.0,
                kiloFt = 0.0,
                hours = "",
                hoursFt = 0.0,
                tr = "", // Pre-populate with empty string
                amount = 0.0,
                documentId = ""
            )

            printDataList.add(printData)
        }

        // Now call refreshTable to update the UI
        refreshTable()
    }

    private fun createTable() {
        createHeaderRow()
    }

    private fun createHeaderRow() {
        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@PrintActivity, R.drawable.table_header_bg)
        }

        headers.forEach { headerText ->
            headerRow.addView(createHeaderTextView(headerText))
        }
        binding.printTableLayout.addView(headerRow)
    }

    private fun createHeaderTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            // Set minimum width to ensure columns are wide enough
            setMinWidth(resources.getDimensionPixelSize(R.dimen.column_min_width))
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
        }
    }

    private fun createDataTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(8, 8, 8, 8)
            textSize = 12f
            // Set minimum width to match header
            setMinWidth(resources.getDimensionPixelSize(R.dimen.column_min_width))
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
        }
    }

    private fun createEditableHoursField(initialValue: String): EditText {
        return EditText(this).apply {
            setText(initialValue)
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(8, 8, 8, 8)
            textSize = 12f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            // Set minimum width to match other columns
            setMinWidth(resources.getDimensionPixelSize(R.dimen.column_min_width))
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }

            // Add TextWatcher for real-time updates
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val row = parent as? TableRow
                    if (row != null) {
                        updateRowCalculations(row)
                    }
                }
            })
        }
    }

    private fun updateRowCalculations(row: TableRow) {
        // Find the hours, hourft, and amount fields in this row
        var hoursValue = 0.0
        var hourFtTextView: TextView? = null
        var amountTextView: TextView? = null
        var kiloFtTextView: TextView? = null
        var dayTextView: TextView? = null
        var totalTextView: TextView? = null

        // Get document ID from row tag
        val documentId = row.tag as? String

        // Iterate through all children in the row to find the correct fields
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            when (i) {
                1 -> dayTextView = child as? TextView // Day column at index 1
                2 -> totalTextView = child as? TextView // Total column at index 2
                7 -> kiloFtTextView = child as TextView // Kilo(ft) column at index 7
                8 -> if (child is EditText) {
                    hoursValue = child.text.toString().toDoubleOrNull() ?: 0.0
                }
                9 -> hourFtTextView = child as? TextView // Hours(ft) column at index 9
                11 -> amountTextView = child as? TextView // Amount column at index 11
            }
        }

        // Check if this row is for Sunday
        val isSunday = dayTextView?.text?.toString()?.equals("Sun", ignoreCase = true) ?: false

        // Calculate Hour(ft) = Hours * 1235
        val hourFt = hoursValue * HOUR_MULTIPLIER

        // Update Hour(ft) display
        hourFtTextView?.let { textView ->
            if (hoursValue > 0.0) {
                textView.text = String.format("%,d", hourFt.toInt())
            } else {
                textView.text = ""
            }
        }

        // Calculate base amount = Kilo(ft) + Hour(ft)
        var baseAmount = 0.0
        amountTextView?.let { amountView ->
            kiloFtTextView?.let { kiloView ->
                val kiloFtValue = if (kiloView.text.toString().isEmpty()) {
                    0.0
                } else {
                    kiloView.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
                }

                baseAmount = kiloFtValue + hourFt
            }
        }

        // Apply Sunday bonus (50% increase) to the total amount
        val totalAmount = if (isSunday) {
            baseAmount * 1.5
        } else {
            baseAmount
        }

        // Update amount display
        amountTextView?.let { textView ->
            if (totalAmount > 0) {
                textView.text = String.format("%,d", totalAmount.toInt())
            } else {
                textView.text = ""
            }
        }

        // Get the total kilo value to check for row highlighting
        val totalKilo = totalTextView?.text?.toString()?.toDoubleOrNull() ?: 0.0

        // Update row background based on total kilo value
        when {
            totalKilo > 500.0 -> {
                row.setBackgroundColor(Color.parseColor("#90EE90")) // Light green for >500kg
            }
            isSunday -> {
                row.setBackgroundResource(R.drawable.table_row_sunday_bg) // Yellow for Sundays
            }
            else -> {
                row.setBackgroundResource(R.drawable.table_row_bg) // Regular background
            }
        }

        // Update the printDataList to match UI changes
        if (documentId != null && documentId.isNotEmpty() && documentId != "null") {
            val index = printDataList.indexOfFirst { it.documentId == documentId }
            if (index >= 0) {
                val updatedData = printDataList[index].copy(
                    hours = if (hoursValue > 0) hoursValue.toString() else "",
                    hoursFt = hourFt,
                    amount = totalAmount
                )
                printDataList[index] = updatedData
            }
        }

        // Refresh totals row after any change
        refreshTotalsOnly()
    }

    private fun createTrSpinner(initialValue: String): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@PrintActivity, R.layout.spinner_item, trOptions)
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }

            minimumWidth = resources.getDimensionPixelSize(R.dimen.column_min_width)

            // Correctly set the initial selection
            val index = trOptions.indexOf(initialValue)
            val initialPosition = if (index >= 0) index else 0
            setSelection(initialPosition)

            // Auto-save when T/R selection changes
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    // Auto-save T/R changes immediately
                    val row = this@apply.parent as? TableRow
                    if (row != null) {
                        val documentId = row.tag.toString()
                        if (documentId.isNotEmpty() && documentId != "null") {
                            val selectedTrValue = trOptions[position]

                            // Get hours value too
                            val hoursEditText = row.getChildAt(8) as? EditText
                            val hoursValue = hoursEditText?.text?.toString() ?: ""

                            val updates = hashMapOf<String, Any>(
                                "hours" to hoursValue,
                                "tr" to selectedTrValue
                            )

                            FirebaseFirestore.getInstance()
                                .collection("counter_data")
                                .document(documentId)
                                .update(updates)
                                .addOnFailureListener { e ->
                                    // Silent fail for auto-save
                                    println("Error auto-saving T/R: ${e.message}")
                                }
                        }
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
    }

    private fun loadDataFromFirestore() {
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

                // Create a map of existing data by date for easy lookup
                val existingDataMap = mutableMapOf<String, CounterData>()
                val existingDocumentsMap = mutableMapOf<String, String>() // date to documentId

                for (document in documents) {
                    try {
                        // Manually create CounterData from document data
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
                            tr = document.getString("tr") ?: "", // Keep the actual value from Firestore
                            timestamp = document.getLong("timestamp") ?: 0L,
                            documentId = document.id
                        )

                        val formattedDate = convertDateFormat(data.date)
                        existingDataMap[formattedDate] = data
                        existingDocumentsMap[formattedDate] = document.id
                    } catch (e: Exception) {
                        // Skip malformed documents
                        println("Error parsing document ${document.id}: ${e.message}")
                    }
                }

                // Update the pre-populated data with existing Firebase data
                for (i in printDataList.indices) {
                    val printData = printDataList[i]
                    val existingData = existingDataMap[printData.date]

                    if (existingData != null) {
                        // Get category values from Firebase
                        val c1 = existingData.c1
                        val c2 = existingData.c2
                        val c3 = existingData.c3
                        val c4 = existingData.c4
                        val hours = existingData.hours
                        val tr = existingData.tr

                        // Calculate Hours(ft)
                        val hoursValue = hours.toDoubleOrNull() ?: 0.0
                        val hoursFt = hoursValue * HOUR_MULTIPLIER

                        // Calculate Amount = Kilo(ft) + Hours(ft)
                        val baseAmount = existingData.totalAmount + hoursFt

                        // Check if it's Sunday and apply 50% increase
                        val isSunday = printData.day.equals("Sun", ignoreCase = true)
                        val finalAmount = if (isSunday) baseAmount * 1.5 else baseAmount

                        // Update the printData with Firebase data
                        printDataList[i] = printData.copy(
                            totalKilo = existingData.totalKilo,
                            c1 = c1,
                            c2 = c2,
                            c3 = c3,
                            c4 = c4,
                            kiloFt = existingData.totalAmount, // Kilo(ft) = total amount from DataActivity
                            hours = hours,
                            hoursFt = hoursFt,
                            tr = tr, // Keep the actual value from Firestore
                            amount = finalAmount, // Apply Sunday bonus if applicable
                            documentId = existingDocumentsMap[printData.date] ?: ""
                        )
                    }
                }

                // Refresh the table with updated data
                refreshTable()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun refreshTable() {
        // Remove all rows except header
        val childCount = binding.printTableLayout.childCount
        if (childCount > 1) {
            binding.printTableLayout.removeViews(1, childCount - 1)
        }

        // Add all data rows
        printDataList.forEach { data ->
            addDataRow(data)
        }

        // Add totals row at the end
        createTotalsRow()
    }

    // Method to refresh only the totals row (more efficient)
    private fun refreshTotalsOnly() {
        // Remove only the last row (totals row) if it exists
        val childCount = binding.printTableLayout.childCount
        if (childCount > 1) {
            val lastRow = binding.printTableLayout.getChildAt(childCount - 1)
            if (lastRow.tag == "totals") {
                binding.printTableLayout.removeViewAt(childCount - 1)
            }
        }

        // Add updated totals row
        val totalRow = createTotalsRowWithTag()
        binding.printTableLayout.addView(totalRow)
    }

    // Helper method to create totals row with tag
    private fun createTotalsRowWithTag(): TableRow {
        // Calculate totals
        var daysWorked = 0
        var totalKilo = 0.0
        var totalC1 = 0.0
        var totalC2 = 0.0
        var totalC3 = 0.0
        var totalC4 = 0.0
        var totalKiloFt = 0.0
        var totalHours = 0.0
        var totalHoursFt = 0.0
        var totalAmount = 0.0

        printDataList.forEach { data ->
            if (data.totalKilo > 0.0 || data.c1 > 0.0 || data.c2 > 0.0 ||
                data.c3 > 0.0 || data.c4 > 0.0 || data.hours.isNotEmpty()) {
                daysWorked++
            }

            totalKilo += data.totalKilo
            totalC1 += data.c1
            totalC2 += data.c2
            totalC3 += data.c3
            totalC4 += data.c4
            totalKiloFt += data.kiloFt
            totalHours += data.hours.toDoubleOrNull() ?: 0.0
            totalHoursFt += data.hoursFt
            totalAmount += data.amount
        }

        return TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@PrintActivity, R.drawable.table_header_bg)
            tag = "totals" // Tag to identify as totals row

            // Add total cells
            addView(createTotalTextView("TOTAL"))
            addView(createTotalTextView(daysWorked.toString()))
            addView(createTotalTextView(String.format("%.1f", totalKilo)))
            addView(createTotalTextView(String.format("%.1f", totalC1)))
            addView(createTotalTextView(String.format("%.1f", totalC2)))
            addView(createTotalTextView(String.format("%.1f", totalC3)))
            addView(createTotalTextView(String.format("%.1f", totalC4)))
            addView(createTotalTextView(String.format("%,d", totalKiloFt.toInt())))
            addView(createTotalTextView(String.format("%.0f", totalHours)))
            addView(createTotalTextView(String.format("%,d", totalHoursFt.toInt())))
            addView(createTotalTextView("")) // Empty cell for T/R column
            addView(createTotalTextView(String.format("%,d", totalAmount.toInt())))
        }
    }

    // Method to create totals row
    private fun createTotalsRow() {
        // Calculate totals
        var daysWorked = 0
        var totalKilo = 0.0
        var totalC1 = 0.0
        var totalC2 = 0.0
        var totalC3 = 0.0
        var totalC4 = 0.0
        var totalKiloFt = 0.0
        var totalHours = 0.0
        var totalHoursFt = 0.0
        var totalAmount = 0.0

        printDataList.forEach { data ->
            // Count days worked (days with any data)
            if (data.totalKilo > 0.0 || data.c1 > 0.0 || data.c2 > 0.0 ||
                data.c3 > 0.0 || data.c4 > 0.0 || data.hours.isNotEmpty()) {
                daysWorked++
            }

            totalKilo += data.totalKilo
            totalC1 += data.c1
            totalC2 += data.c2
            totalC3 += data.c3
            totalC4 += data.c4
            totalKiloFt += data.kiloFt
            totalHours += data.hours.toDoubleOrNull() ?: 0.0
            totalHoursFt += data.hoursFt
            totalAmount += data.amount
        }

        // Create totals row
        val totalRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@PrintActivity, R.drawable.table_header_bg)
            tag = "totals" // Tag to identify as totals row
        }

        // Add total cells
        totalRow.addView(createTotalTextView("TOTAL"))
        totalRow.addView(createTotalTextView(daysWorked.toString()))
        totalRow.addView(createTotalTextView(String.format("%.1f", totalKilo)))
        totalRow.addView(createTotalTextView(String.format("%.1f", totalC1)))
        totalRow.addView(createTotalTextView(String.format("%.1f", totalC2)))
        totalRow.addView(createTotalTextView(String.format("%.1f", totalC3)))
        totalRow.addView(createTotalTextView(String.format("%.1f", totalC4)))
        totalRow.addView(createTotalTextView(String.format("%,d", totalKiloFt.toInt())))
        totalRow.addView(createTotalTextView(String.format("%.0f", totalHours)))
        totalRow.addView(createTotalTextView(String.format("%,d", totalHoursFt.toInt())))
        totalRow.addView(createTotalTextView("")) // Empty cell for T/R column
        totalRow.addView(createTotalTextView(String.format("%,d", totalAmount.toInt())))

        binding.printTableLayout.addView(totalRow)
    }

    // Helper method for total text views
    private fun createTotalTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setMinWidth(resources.getDimensionPixelSize(R.dimen.column_min_width))
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
        }
    }

    private fun convertDateFormat(dateString: String): String {
        return try {
            // Try to parse the original format first
            val originalFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val date = originalFormat.parse(dateString)

            // Format to the new format (YYYY.MM.DD)
            val newFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            newFormat.format(date ?: Date())
        } catch (e: Exception) {
            // If parsing fails, try alternative formats or return original
            try {
                // Try another common format
                val alternativeFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = alternativeFormat.parse(dateString)
                val newFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                newFormat.format(date ?: Date())
            } catch (e2: Exception) {
                dateString // Return original if all parsing fails
            }
        }
    }

    private fun addDataRow(data: PrintData) {
        val row = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )

            // Set row background based on conditions:
            // 1. Light green if total kilo > 500 (priority)
            // 2. Yellow for Sundays
            // 3. Regular row background
            when {
                data.totalKilo > 500.0 -> {
                    setBackgroundColor(Color.parseColor("#90EE90")) // Light green for >500kg
                }
                data.day.equals("Sun", ignoreCase = true) -> {
                    setBackgroundResource(R.drawable.table_row_sunday_bg) // Yellow for Sundays
                }
                else -> {
                    setBackgroundResource(R.drawable.table_row_bg) // Regular background
                }
            }

            tag = data.documentId // Store document ID for reference
        }

        // Check if this row has any actual data (not just pre-populated zeros)
        val hasData = data.totalKilo > 0.0 || data.c1 > 0.0 || data.c2 > 0.0 ||
                data.c3 > 0.0 || data.c4 > 0.0 || data.amount > 0.0 ||
                data.hours.isNotEmpty()

        // Date column
        row.addView(createDataTextView(data.date))

        // Day column
        row.addView(createDataTextView(data.day))

        // Total column (total kilo) - no individual highlighting, just show value
        val totalText = if (!hasData && data.totalKilo == 0.0) "" else String.format("%.1f", data.totalKilo)
        row.addView(createDataTextView(totalText))

        // C1, C2, C3, C4 columns - no individual highlighting
        val c1Text = if (!hasData && data.c1 == 0.0) "" else String.format("%.1f", data.c1)
        row.addView(createDataTextView(c1Text))

        val c2Text = if (!hasData && data.c2 == 0.0) "" else String.format("%.1f", data.c2)
        row.addView(createDataTextView(c2Text))

        val c3Text = if (!hasData && data.c3 == 0.0) "" else String.format("%.1f", data.c3)
        row.addView(createDataTextView(c3Text))

        val c4Text = if (!hasData && data.c4 == 0.0) "" else String.format("%.1f", data.c4)
        row.addView(createDataTextView(c4Text))

        // Kilo(ft) column - no individual highlighting
        val kiloFtText = if (!hasData && data.kiloFt == 0.0) "" else String.format("%,d", data.kiloFt.toInt())
        row.addView(createDataTextView(kiloFtText))

        // Hours column (editable)
        val hoursEditText = createEditableHoursField(data.hours)
        row.addView(hoursEditText)

        // Hours(ft) column - no individual highlighting
        val hoursFtText = if (!hasData && data.hoursFt == 0.0) "" else String.format("%,d", data.hoursFt.toInt())
        row.addView(createDataTextView(hoursFtText))

        // T/R column (dropdown) - FIXED: Now correctly shows saved values
        val trSpinner = createTrSpinner(data.tr)
        row.addView(trSpinner)

        // Amount column - no individual highlighting
        val amountText = if (!hasData && data.amount == 0.0) "" else String.format("%,d", data.amount.toInt())
        row.addView(createDataTextView(amountText))

        binding.printTableLayout.addView(row)
    }

    private fun saveChangesToFirestore() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        // Iterate through all rows (skip header row at index 0 and totals row at the end)
        val childCount = binding.printTableLayout.childCount
        for (i in 1 until childCount - 1) { // Skip header and totals
            val row = binding.printTableLayout.getChildAt(i) as TableRow
            val documentId = row.tag.toString()

            // Find the editable fields in this row
            var hoursValue = ""
            var trValue = ""

            // Get the hours value from the EditText (column index 8)
            val hoursEditText = row.getChildAt(8) as? EditText
            hoursValue = hoursEditText?.text?.toString() ?: ""

            // Get the T/R value from the Spinner (column index 10)
            val trSpinner = row.getChildAt(10) as? Spinner
            trValue = trSpinner?.selectedItem?.toString() ?: ""

            // Save if there's a documentId AND (hours value OR T/R value is not empty)
            if (documentId.isNotEmpty() && documentId != "null" &&
                (hoursValue.isNotEmpty() || trValue.isNotEmpty())) {

                val updates = hashMapOf<String, Any>(
                    "hours" to hoursValue,
                    "tr" to trValue
                )

                db.collection("counter_data")
                    .document(documentId)
                    .update(updates)
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error saving changes: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        Toast.makeText(this, "Changes saved successfully", Toast.LENGTH_SHORT).show()
    }

    // Export data to Excel file using JXL (Android-friendly)
    private fun exportToExcel() {
        try {
            // Create file name with current date
            val dateFormat = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
            val fileName = "Summary_${dateFormat.format(Date())}.xls"

            // Get output stream based on Android version
            var outputFile: File? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above, create a temporary file first
                outputFile = File(cacheDir, fileName)
            } else {
                // For older Android versions
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                outputFile = File(downloadsDir, fileName)
            }

            // Create a new workbook
            val workbook = Workbook.createWorkbook(outputFile)
            val sheet = workbook.createSheet("Print Summary", 0)

            // Create formats
            val headerFormat = WritableCellFormat(WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD))
            headerFormat.setBackground(Colour.BLUE)

            val sundayFormat = WritableCellFormat()
            sundayFormat.setBackground(Colour.YELLOW)

            val highlightFormat = WritableCellFormat() // For rows with >500kg
            highlightFormat.setBackground(Colour.LIGHT_GREEN)

            val normalFormat = WritableCellFormat()

            // Add header row
            headers.forEachIndexed { index, header ->
                sheet.addCell(Label(index, 0, header, headerFormat))
            }

            // Add data rows
            printDataList.forEachIndexed { index, data ->
                val rowIndex = index + 1
                val isSunday = data.day.equals("Sun", ignoreCase = true)

                // Determine row format based on total kilo (priority: >500kg > Sunday > normal)
                val rowFormat = when {
                    data.totalKilo > 500.0 -> highlightFormat // Light green for >500kg
                    isSunday -> sundayFormat // Yellow for Sundays
                    else -> normalFormat // Regular
                }

                // Add all cells with the same row format
                sheet.addCell(Label(0, rowIndex, data.date, rowFormat))
                sheet.addCell(Label(1, rowIndex, data.day, rowFormat))
                sheet.addCell(Number(2, rowIndex, data.totalKilo, rowFormat))
                sheet.addCell(Number(3, rowIndex, data.c1, rowFormat))
                sheet.addCell(Number(4, rowIndex, data.c2, rowFormat))
                sheet.addCell(Number(5, rowIndex, data.c3, rowFormat))
                sheet.addCell(Number(6, rowIndex, data.c4, rowFormat))
                sheet.addCell(Number(7, rowIndex, data.kiloFt, rowFormat))
                sheet.addCell(Number(8, rowIndex, data.hours.toDoubleOrNull() ?: 0.0, rowFormat))
                sheet.addCell(Number(9, rowIndex, data.hoursFt, rowFormat))
                sheet.addCell(Label(10, rowIndex, data.tr, rowFormat))
                sheet.addCell(Number(11, rowIndex, data.amount, rowFormat))
            }

            // Add totals row
            val totalsRowIndex = printDataList.size + 1
            var daysWorked = 0
            var totalKilo = 0.0
            var totalC1 = 0.0
            var totalC2 = 0.0
            var totalC3 = 0.0
            var totalC4 = 0.0
            var totalKiloFt = 0.0
            var totalHours = 0.0
            var totalHoursFt = 0.0
            var totalAmount = 0.0

            printDataList.forEach { data ->
                if (data.totalKilo > 0.0 || data.c1 > 0.0 || data.c2 > 0.0 ||
                    data.c3 > 0.0 || data.c4 > 0.0 || data.hours.isNotEmpty()) {
                    daysWorked++
                }
                totalKilo += data.totalKilo
                totalC1 += data.c1
                totalC2 += data.c2
                totalC3 += data.c3
                totalC4 += data.c4
                totalKiloFt += data.kiloFt
                totalHours += data.hours.toDoubleOrNull() ?: 0.0
                totalHoursFt += data.hoursFt
                totalAmount += data.amount
            }

            // Add totals with header formatting
            sheet.addCell(Label(0, totalsRowIndex, "TOTAL", headerFormat))
            sheet.addCell(Number(1, totalsRowIndex, daysWorked.toDouble(), headerFormat))
            sheet.addCell(Number(2, totalsRowIndex, totalKilo, headerFormat))
            sheet.addCell(Number(3, totalsRowIndex, totalC1, headerFormat))
            sheet.addCell(Number(4, totalsRowIndex, totalC2, headerFormat))
            sheet.addCell(Number(5, totalsRowIndex, totalC3, headerFormat))
            sheet.addCell(Number(6, totalsRowIndex, totalC4, headerFormat))
            sheet.addCell(Number(7, totalsRowIndex, totalKiloFt, headerFormat))
            sheet.addCell(Number(8, totalsRowIndex, totalHours, headerFormat))
            sheet.addCell(Number(9, totalsRowIndex, totalHoursFt, headerFormat))
            sheet.addCell(Label(10, totalsRowIndex, "", headerFormat))
            sheet.addCell(Number(11, totalsRowIndex, totalAmount, headerFormat))

            // Write and close the workbook
            workbook.write()
            workbook.close()

            // For Android 10+, copy the file to Downloads using MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-excel")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    // Delete the temporary file
                    outputFile.delete()
                }
            }

            Toast.makeText(this, "Excel file exported to Downloads folder: $fileName", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error exporting to Excel: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

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
                    startActivity(Intent(this, DataActivity::class.java))
                    true
                }
                R.id.menu_summary -> {
                    // Already in PrintActivity, do nothing or refresh
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