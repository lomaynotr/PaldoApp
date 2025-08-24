package com.example.paldoapp

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.paldoapp.databinding.ActivityDataBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// Define CounterData class
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
    private val headers = arrayOf("Date", "Total Kilo", "Total Amount", "Action")
    private var allDataList = mutableListOf<CounterData>()

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
        binding.btnPrintSummary.setOnClickListener {
            val intent = Intent(this, PrintActivity::class.java)
            startActivity(intent)
        }
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
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)

            setOnClickListener {
                showDeleteConfirmationDialog(data)
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
                // Remove from local list
                allDataList.removeAll { it.documentId == data.documentId }
                // Refresh the table
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

        // Try a simpler query first without orderBy to avoid index issues
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

                // Sort by timestamp manually (oldest first aug1 --> aug31)
                allDataList.sortBy{ it.timestamp }

                updateTable()
                updateTotals()

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

        // Add data rows
        allDataList.forEach { data ->
            val row = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundResource(R.drawable.table_row_bg)
            }

            // Date column
            row.addView(createDataTextView(data.date))

            // Total Kilo column
            row.addView(createDataTextView(String.format("%.1f", data.totalKilo)))

            // Total Amount column
            row.addView(createDataTextView(String.format("%.2f", data.totalAmount)))

            // Delete button column
            row.addView(createDeleteButton(data))

            binding.dataTableLayout.addView(row)
        }
    }

    private fun updateTotals() {
        var totalKilo = 0.0
        var totalAmount = 0.0

        allDataList.forEach { data ->
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

        // Total Amount
        totalRow.addView(createTotalTextView(String.format("%.2f", totalAmount)))

        // Empty cell for delete column
        totalRow.addView(createTotalTextView(""))

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
}