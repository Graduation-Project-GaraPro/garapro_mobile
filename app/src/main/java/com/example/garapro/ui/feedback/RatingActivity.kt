package com.example.garapro.ui.feedback

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.garapro.R

class RatingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rating)

        val ratingBar = findViewById<RatingBar>(R.id.ratingBar)
        val edtFeedback = findViewById<EditText>(R.id.edtFeedback)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating
            val feedback = edtFeedback.text.toString()

            Toast.makeText(
                this,
                "Bạn đã đánh giá $rating sao\nNội dung: $feedback",
                Toast.LENGTH_LONG
            ).show()

            // TODO: gửi rating về server hoặc lưu database
        }
    }
}