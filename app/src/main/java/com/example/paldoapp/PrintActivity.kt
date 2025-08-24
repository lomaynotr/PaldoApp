package com.example.paldoapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paldoapp.databinding.ActivityPrintBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class PrintActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrintBinding
    private val headers = arrayOf("Date", "Day", "Total", "C1", "C2", "C3", "C4", "Kilo(ft)", "Hours", "Hour(ft)", "T/R", "Amount")
    private var printDataList = mutableListOf<PrintData>()
    private val trOptions = arrayOf("T", "R")
    private val HOUR_MULTIPLIER = 1235

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createTable()
        loadDataFromFirestore()

        // Remove the save changes button click listener since we removed the button
        // Auto-save will happen when user changes Hours or T/R values
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

            // Auto-save when user changes hours
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val row = parent as TableRow
                    updateRowCalculations(row)
                    saveChangesToFirestore()
                }
            }
        }
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

            // Set minimum width to match other columns
            minimumWidth = resources.getDimensionPixelSize(R.dimen.column_min_width)

            // Set initial selection
            val initialPosition = trOptions.indexOf(initialValue)
            if (initialPosition >= 0) {
                setSelection(initialPosition)
            }

            // Auto-save when user changes T/R
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val row = this@apply.parent as? TableRow
                    if (row != null) {
                        saveChangesToFirestore()
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

        // Remove orderBy to avoid index issues
        FirebaseFirestore.getInstance()
            .collection("counter_data")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                printDataList.clear()

                // Remove all rows except header
                val childCount = binding.printTableLayout.childCount
                if (childCount > 1) {
                    binding.printTableLayout.removeViews(1, childCount - 1)
                }

                val tempDataList = mutableListOf<CounterData>()

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
                            tr = document.getString("tr") ?: "T",
                            timestamp = document.getLong("timestamp") ?: 0L,
                            documentId = document.id
                        )
                        tempDataList.add(data)
                    } catch (e: Exception) {
                        println("Error parsing document ${document.id}: ${e.message}")
                    }
                }

                // Sort by date (oldest first) - using the original date format for accurate sorting
                tempDataList.sortBy { data ->
                    try {
                        // Parse the original date format for proper chronological sorting
                        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                        dateFormat.parse(data.date)?.time ?: 0L
                    } catch (e: Exception) {
                        // Fallback to timestamp if date parsing fails
                        data.timestamp
                    }
                }

                // Pre-populate all dates in the current month
                val allDatesData = generateAllDatesForCurrentMonth(tempDataList)

                // Process each date entry (includes both existing data and empty dates)
                for (data in allDatesData) {
                    // Convert the date to the new format (YYYY.MM.DD)
                    val formattedDate = convertDateFormat(data.date)

                    // Convert CounterData to PrintData
                    val printData = PrintData(
                        date = formattedDate,
                        day = getDayFromDate(formattedDate),
                        totalKilo = data.totalKilo,
                        c1 = data.c1,
                        c2 = data.c2,
                        c3 = data.c3,
                        c4 = data.c4,
                        hours = data.hours,
                        tr = data.tr,
                        amount = data.totalAmount,
                        documentId = data.documentId
                    )

                    printDataList.add(printData)
                    addDataRow(printData)
                }

                Toast.makeText(this, "Loaded ${printDataList.size} entries for printing", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                println("PrintActivity Firestore error: ${e.localizedMessage}")
            }
    }

    private fun generateAllDatesForCurrentMonth(existingData: List<CounterData>): List<CounterData> {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        // Get the number of days in current month
        calendar.set(currentYear, currentMonth, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val allDatesData = mutableListOf<CounterData>()

        // Generate all dates for current month
        for (day in 1..daysInMonth) {
            calendar.set(currentYear, currentMonth, day)
            val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val dateString = dateFormat.format(calendar.time)

            // Check if we have existing data for this date
            val existingEntry = existingData.find { it.date == dateString }

            if (existingEntry != null) {
                // Use existing data
                allDatesData.add(existingEntry)
            } else {
                // Create empty entry for this date
                val emptyEntry = CounterData(
                    userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    date = dateString,
                    totalKilo = 0.0,
                    totalAmount = 0.0,
                    c1 = 0.0,
                    c2 = 0.0,
                    c3 = 0.0,
                    c4 = 0.0,
                    hours = "",
                    tr = "T",
                    timestamp = calendar.timeInMillis,
                    documentId = "" // Empty for new entries
                )
                allDatesData.add(emptyEntry)
            }
        }

        return allDatesData
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

    private fun getDayFromDate(dateString: String): String {
        return try {
            // Parse the new format (YYYY.MM.DD)
            val inputFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            dayFormat.format(date ?: Date())
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun addDataRow(data: PrintData) {
        val row = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundResource(R.drawable.table_row_bg)
            tag = data.documentId // Store document ID for reference
        }

        // Date column
        row.addView(createDataTextView(data.date))

        // Day column
        row.addView(createDataTextView(data.day))

        // Total column (total kilo)
        row.addView(createDataTextView(String.format("%.1f", data.totalKilo)))

        // C1, C2, C3, C4 columns - now showing actual data from user input
        row.addView(createDataTextView(String.format("%.1f", data.c1)))
        row.addView(createDataTextView(String.format("%.1f", data.c2)))
        row.addView(createDataTextView(String.format("%.1f", data.c3)))
        row.addView(createDataTextView(String.format("%.1f", data.c4)))

        // Kilo(ft) column - FIXED: should show the original amount from DataActivity, not totalKilo
        row.addView(createDataTextView(String.format("%.2f", data.amount)))

        // Hours column (editable)
        val hoursEditText = createEditableHoursField(data.hours)
        hoursEditText.tag = "hours" // Tag to identify this field later
        row.addView(hoursEditText)

        // Hour(ft) column - calculated field (Hours * 1235)
        val hours = data.hours.toDoubleOrNull() ?: 0.0
        val hourFt = hours * HOUR_MULTIPLIER
        val hourFtTextView = createDataTextView(String.format("%.0f", hourFt))
        hourFtTextView.tag = "hourft"
        row.addView(hourFtTextView)

        // T/R column (dropdown)
        val trSpinner = createTrSpinner(data.tr)
        trSpinner.tag = "tr" // Tag to identify this field later
        row.addView(trSpinner)

        // Amount column - updated calculation (original amount + Hour(ft))
        val totalAmount = data.amount + hourFt
        val amountTextView = createDataTextView(String.format("%.2f", totalAmount))
        amountTextView.tag = "amount"
        row.addView(amountTextView)

        binding.printTableLayout.addView(row)
    }

    private fun updateRowCalculations(row: TableRow) {
        // Find the hours, hourft, and amount fields in this row
        var hoursValue = 0.0
        var hourFtTextView: TextView? = null
        var amountTextView: TextView? = null
        var originalAmount = 0.0

        // Get the document ID to find original amount
        val documentId = row.tag.toString()
        val originalData = printDataList.find { it.documentId == documentId }
        originalAmount = originalData?.amount ?: 0.0

        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            when (child.tag) {
                "hours" -> {
                    hoursValue = (child as EditText).text.toString().toDoubleOrNull() ?: 0.0
                }
                "hourft" -> {
                    hourFtTextView = child as TextView
                }
                "amount" -> {
                    amountTextView = child as TextView
                }
            }
        }

        // Calculate and update Hour(ft)
        val hourFt = hoursValue * HOUR_MULTIPLIER
        hourFtTextView?.text = String.format("%.0f", hourFt)

        // Calculate and update Amount (original amount + Hour(ft))
        val totalAmount = originalAmount + hourFt
        amountTextView?.text = String.format("%.2f", totalAmount)
    }

    private fun saveChangesToFirestore() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) return

        // Iterate through all rows (skip header row at index 0)
        for (i in 1 until binding.printTableLayout.childCount) {
            val row = binding.printTableLayout.getChildAt(i) as TableRow
            val documentId = row.tag.toString()

            // Find the editable fields in this row
            var hoursValue = ""
            var trValue = ""

            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j)
                when (child.tag) {
                    "hours" -> hoursValue = (child as EditText).text.toString()
                    "tr" -> trValue = (child as Spinner).selectedItem.toString()
                }
            }

            // Only save if there's actual data (hours is not empty)
            if (hoursValue.isNotEmpty()) {
                if (documentId.isNotEmpty()) {
                    // Update existing document
                    val updates = hashMapOf<String, Any>(
                        "hours" to hoursValue,
                        "tr" to trValue
                    )

                    db.collection("counter_data")
                        .document(documentId)
                        .update(updates)
                        .addOnFailureListener { e ->
                            println("Error auto-saving changes: ${e.message}")
                        }
                } else {
                    // Create new document for empty date entries that now have hours
                    val printData = printDataList.find { it.documentId == "" }
                    if (printData != null) {
                        val newDocumentData = hashMapOf(
                            "userId" to currentUser.uid,
                            "date" to printData.date,
                            "totalKilo" to 0.0,
                            "totalAmount" to 0.0,
                            "c1" to 0.0,
                            "c2" to 0.0,
                            "c3" to 0.0,
                            "c4" to 0.0,
                            "hours" to hoursValue,
                            "tr" to trValue,
                            "timestamp" to System.currentTimeMillis()
                        )

                        db.collection("counter_data")
                            .add(newDocumentData)
                            .addOnSuccessListener { docRef ->
                                // Update the row tag with the new document ID
                                row.tag = docRef.id
                            }
                            .addOnFailureListener { e ->
                                println("Error creating new document: ${e.message}")
                            }
                    }
                }
            }
        }
    }
}

// Data class to hold print data with additional fields
data class PrintData(
    val date: String = "",
    val day: String = "",
    val totalKilo: Double = 0.0,
    val c1: Double = 0.0,
    val c2: Double = 0.0,
    val c3: Double = 0.0,
    val c4: Double = 0.0,
    val hours: String = "",
    val tr: String = "",
    val amount: Double = 0.0,
    val documentId: String = "" // For Firestore reference
)