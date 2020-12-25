package de.koenidv.sph.adapters

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.koenidv.sph.R
import de.koenidv.sph.SphPlanner
import de.koenidv.sph.objects.TimetableEntry
import de.koenidv.sph.parsing.CourseParser
import de.koenidv.sph.parsing.Utility

//  Created by koenidv on 18.12.2020.
class LessonsAdapter(private var dataset: List<List<TimetableEntry>>,
                     private var expanded: Boolean = false,
                     private var multiple: Boolean = false, private var maxConcurrent: Int = 1,
                     private val onClick: (List<TimetableEntry>) -> Unit) :
        RecyclerView.Adapter<LessonsAdapter.ViewHolder>() {

    /**
     * Provides a reference to the type of view
     * (custom ViewHolder).
     */
    class ViewHolder(view: View, val onClick: (List<TimetableEntry>) -> Unit) : RecyclerView.ViewHolder(view) {
        val layout: LinearLayout = view.findViewById(R.id.itemLayout)
        private val textView: TextView = view.findViewById(R.id.courseNameTextView)
        private var currentEntry: List<TimetableEntry>? = null

        init {
            // Set onClickListener from attribute
            layout.setOnClickListener {
                currentEntry?.let {
                    onClick(it)
                }
            }
        }

        fun bind(entries: List<TimetableEntry>, hourcount: Int, expanded: Boolean, multiple: Boolean, maxConcurrent: Int = 1) {
            if (!multiple) currentEntry = entries

            // Set data
            var title = ""
            if (!multiple) {
                title = CourseParser().getShortnameFromInternald(entries[0].lesson.idCourse)
                if (expanded) title += "<br><small>${entries[0].lesson.room}</small>"
            } else {
                entries.forEach {
                    title += "${CourseParser().getShortnameFromInternald(it.lesson.idCourse)} (${it.course?.id_teacher})"
                    if (expanded) title += "<br><small>${it.lesson.room}<br></small>"
                    title += "<br>"
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                textView.text = Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                textView.text = Html.fromHtml(title)
            }

            // Set size
            // Enlarge to show rooms
            // Or span multiple hours if consecutive lessons are the same
            val params = layout.layoutParams
            val height = if (expanded && !multiple) 64f else if (multiple) maxConcurrent * 32f else 32f
            val extrapadding = (hourcount - 1) * 4f
            params.height = Utility().dpToPx(hourcount * height + extrapadding).toInt()
            layout.layoutParams = params

            val color: Int = if (!multiple) {
                (entries[0].course?.color
                        ?: 6168631)
            } else {
                SphPlanner.applicationContext().getColor(R.color.grey_800)
            }
            // Set background color with 40% opacity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (layout.background as StateListDrawable).colorFilter = BlendModeColorFilter(
                        color and 0x00FFFFFF or 0x66000000, BlendMode.SRC_ATOP)
            } else {
                @Suppress("DEPRECATION") // not in < Q
                (layout.background as StateListDrawable)
                        .setColorFilter(color and 0x00FFFFFF or 0x66000000, PorterDuff.Mode.SRC_ATOP)
            }
        }
    }

    // Creates new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.item_lesson, viewGroup, false)

        return ViewHolder(view, onClick)
    }

    // Replaces the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // Bind data to ViewHolder
        // sph will not mix different rowspans, therefore we can just check the first item
        if (!dataset.getOrNull(position).isNullOrEmpty()
                && dataset[position][0].lesson.isDisplayed != true) {

            // todo better check for same lessons (includes..)
            // Check if the next lessons and changes are the same
            // Ignore rooms if not expanded
            // Ignore changes if concurrent courses are shown
            // Hide them if they are
            var hourcount = 1
            val thisLesson = dataset[position][0].lesson
            var nextLesson = dataset.getOrNull(position + hourcount)?.getOrNull(0)?.lesson
            var nextChanges = dataset.getOrNull(position + hourcount)?.getOrNull(0)?.changes
            while (nextLesson != null
                    && nextLesson.idCourse == thisLesson.idCourse
                    && (!expanded || nextLesson.room == thisLesson.room)
                    && (multiple || dataset[position][0].changes == nextChanges)) {
                // Hide next lesson
                dataset[position + hourcount][0].lesson.isDisplayed = true
                // Get the next, next lessen
                hourcount++
                nextLesson = dataset.getOrNull(position + hourcount)?.getOrNull(0)?.lesson
                nextChanges = dataset.getOrNull(position + hourcount)?.getOrNull(0)?.changes
            }

            // Make sure ViewHolder is visible after layout changed
            viewHolder.layout.visibility = View.VISIBLE

            // Bind lesson to view
            viewHolder.bind(dataset[position], hourcount, expanded, multiple, maxConcurrent)

        } else if (!dataset.getOrNull(position).isNullOrEmpty()
                && dataset[position][0].lesson.isDisplayed == true) {
            // Lesson is already displayed
            viewHolder.layout.visibility = View.GONE
        } else {
            // No lesson for this hour
            viewHolder.layout.visibility = View.INVISIBLE
            // We still need to apply the correct size
            val params = viewHolder.layout.layoutParams
            val height = if (expanded) 64f else 32f
            params.height = Utility().dpToPx(height).toInt()
            viewHolder.layout.layoutParams = params
        }
        // todo check for rooms if expanded, don't if not
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataset.size

    fun setExpanded(expanded: Boolean) {
        this.expanded = expanded
        notifyDataSetChanged()
    }

    fun setDataAndMultiple(newDataset: List<List<TimetableEntry>>, multiple: Boolean, maxConcurrent: Int) {
        this.dataset = newDataset
        this.multiple = multiple
        this.maxConcurrent = maxConcurrent
        notifyDataSetChanged()
    }

}