package com.codabookmarker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView

class TokenInputView(
    context: Context,
    suggestions: List<String>,
) : FlowLayout(context) {
    private val tokens = mutableListOf<String>()
    private val input = AutoCompleteTextView(context).apply {
        hint = "Add option"
        threshold = 0
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_DONE
        setPadding(dp(6), dp(7), dp(6), dp(7))
        setTextColor(Color.rgb(242, 243, 247))
        setHintTextColor(Color.rgb(174, 181, 196))
        setDropDownBackgroundDrawable(
            roundedBackground(Color.rgb(26, 29, 36), Color.rgb(61, 67, 82), 1),
        )
        background = null
    }
    private val allSuggestions = suggestions
        .map(::normalize)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)

    init {
        setPadding(dp(6), dp(5), dp(6), dp(5))
        background = roundedBackground(Color.rgb(34, 38, 48), Color.rgb(61, 67, 82), 1)
        addView(input, LayoutParams(dp(140), dp(42)))
        updateSuggestions()

        input.setOnItemClickListener { parent, _, position, _ ->
            addToken(parent.getItemAtPosition(position).toString())
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && allSuggestions.isNotEmpty()) input.showDropDown()
        }
        input.setOnClickListener {
            if (allSuggestions.isNotEmpty()) input.showDropDown()
        }
        input.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                commitDraft()
                true
            } else {
                false
            }
        }
        input.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when {
                keyCode == KeyEvent.KEYCODE_DEL && input.text.isEmpty() && tokens.isNotEmpty() -> {
                    removeToken(tokens.last())
                    true
                }
                keyCode == KeyEvent.KEYCODE_COMMA -> {
                    commitDraft()
                    true
                }
                else -> false
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            private var committing = false

            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(value: Editable?) {
                if (committing || value == null || !value.contains(",")) return
                committing = true
                val parts = value.toString().split(",")
                input.setText(parts.last())
                input.setSelection(input.text.length)
                parts.dropLast(1).forEach(::addToken)
                committing = false
            }
        })
        setOnClickListener { input.requestFocus() }
    }

    fun values(): List<String> = tokens.toList()

    fun setValues(values: List<String>) {
        tokens.clear()
        values.forEach { value ->
            val normalized = normalize(value)
            if (normalized.isNotBlank() && tokens.none { sameValue(it, normalized) }) {
                tokens += normalized
            }
        }
        renderTokens()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        input.isEnabled = enabled
        for (index in 0 until childCount) {
            getChildAt(index).isEnabled = enabled
        }
    }

    private fun commitDraft() {
        addToken(input.text.toString())
    }

    private fun addToken(rawValue: String) {
        val value = normalize(rawValue)
        input.setText("")
        if (value.isBlank() || tokens.any { sameValue(it, value) }) return
        tokens += value
        renderTokens()
        input.requestFocus()
    }

    private fun removeToken(value: String) {
        tokens.removeAll { sameValue(it, value) }
        renderTokens()
        input.requestFocus()
    }

    private fun renderTokens() {
        removeAllViews()
        tokens.forEach { token ->
            addView(TextView(context).apply {
                text = "$token  ×"
                textSize = 13f
                setTextColor(Color.rgb(205, 212, 255))
                setPadding(dp(10), dp(7), dp(8), dp(7))
                background = roundedBackground(
                    Color.rgb(48, 55, 91),
                    Color.rgb(101, 116, 216),
                    1,
                )
                setOnClickListener { removeToken(token) }
                contentDescription = "Remove $token"
            })
        }
        addView(input, LayoutParams(dp(140), dp(42)))
        input.hint = if (tokens.isEmpty()) "Add option" else ""
        updateSuggestions()
        requestLayout()
    }

    private fun updateSuggestions() {
        val available = allSuggestions.filter { suggestion ->
            tokens.none { sameValue(it, suggestion) }
        }
        input.setAdapter(object : ArrayAdapter<String>(
            context,
            android.R.layout.simple_dropdown_item_1line,
            available,
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    setBackgroundColor(Color.rgb(26, 29, 36))
                    (this as? TextView)?.setTextColor(Color.rgb(242, 243, 247))
                }
        })
    }

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    private fun sameValue(left: String, right: String): Boolean =
        left.equals(right, ignoreCase = true)

    private fun roundedBackground(fill: Int, stroke: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fill)
            setStroke(dp(strokeWidth), stroke)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

open class FlowLayout(context: Context) : ViewGroup(context) {
    private val gap = (6 * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var lineWidth = 0
        var lineHeight = 0
        var totalHeight = paddingTop + paddingBottom

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight
            if (lineWidth > 0 && lineWidth + gap + childWidth > maxWidth) {
                totalHeight += lineHeight + gap
                lineWidth = 0
                lineHeight = 0
            }
            lineWidth += (if (lineWidth == 0) 0 else gap) + childWidth
            lineHeight = maxOf(lineHeight, childHeight)
        }
        totalHeight += lineHeight
        setMeasuredDimension(
            resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val maxRight = right - left - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            if (x > paddingLeft && x + child.measuredWidth > maxRight) {
                x = paddingLeft
                y += lineHeight + gap
                lineHeight = 0
            }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gap
            lineHeight = maxOf(lineHeight, child.measuredHeight)
        }
    }
}
