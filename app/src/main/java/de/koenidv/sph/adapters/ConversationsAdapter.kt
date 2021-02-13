package de.koenidv.sph.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import de.koenidv.sph.R
import de.koenidv.sph.SphPlanner.Companion.applicationContext
import de.koenidv.sph.database.UsersDb
import de.koenidv.sph.objects.Conversation
import de.koenidv.sph.parsing.Utility
import java.text.SimpleDateFormat
import java.util.*


//  Created by koenidv on 20.12.2020.
class ConversationsAdapter(private val conversations: List<Conversation>,
                           private val activity: FragmentActivity,
                           private val onSelectModeChange: (Boolean) -> Unit) :
        RecyclerView.Adapter<ConversationsAdapter.ViewHolder>() {

    private val selectedItems = mutableListOf<Int>()
    private var selectMode = false

    private fun selectItem(position: Int) {
        if (selectedItems.contains(position)) {
            // Items was already selected, remove selection
            selectedItems.remove(position)
            // Disable select mode if this was the last item
            if (selectedItems.isEmpty()) {
                selectMode = false
                onSelectModeChange(false)
            }
        } else {
            selectedItems.add(position)
            selectMode = true
            onSelectModeChange(true)
        }
        notifyItemChanged(position)
    }

    private val onClick: (Conversation, Int) -> Unit = { conversation, position ->
        if (selectMode) {
            selectItem(position)
        } else {
            Navigation.findNavController(activity, R.id.nav_host_fragment)
                    .navigate(R.id.chatFromConversationsAction,
                            bundleOf("conversationId" to conversation.convId))
        }
    }
    private val onLongClick: (Int) -> Unit = {
        selectItem(it)
    }

    // Get theme color
    private val prefs = applicationContext()
            .getSharedPreferences("sharedPrefs", AppCompatActivity.MODE_PRIVATE)
    private val themeColor = prefs.getInt("themeColor", 0)

    /**
     * Provides a reference to the type of view
     * (custom ViewHolder).
     */
    class ViewHolder(view: View,
                     onClick: (Conversation, Int) -> Unit,
                     onLongClick: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
        private val layout = view.findViewById<ConstraintLayout>(R.id.conversationLayout)
        private val subject = view.findViewById<TextView>(R.id.subjectTextView)
        private val participants = view.findViewById<TextView>(R.id.participantsTextView)
        private val date = view.findViewById<TextView>(R.id.dateTextView)

        private var currentConversation: Conversation? = null

        init {
            layout.setOnClickListener {
                currentConversation?.let {
                    onClick(it, adapterPosition)
                }
            }

            layout.setOnLongClickListener {
                onLongClick(adapterPosition)
                true
            }

        }


        fun bind(conversation: Conversation, isSelected: Boolean, themeColor: Int) {
            currentConversation = conversation

            subject.text = conversation.subject
            participants.text = getRecipientText(conversation)
            date.text = getRelativeDate(conversation.date)

            // If item is selected, adjust background color
            if (isSelected) {
                // Item now selected
                Utility.tintBackground(layout, themeColor, 0x52000000)
            } else {
                // Item now unselected
                layout.background.clearColorFilter()
            }
        }

        private fun getRelativeDate(date: Date): String {
            val now = Date()

            return if (now.date == date.date &&
                    now.time - date.time < 24 * 360000) {
                // If now is the same day in month and maximum of 24hours ago
                SimpleDateFormat(applicationContext().getString(R.string.messages_dateformat_today),
                        Locale.getDefault())
                        .format(date)
            } else if (now.time - date.time < 48 * 360000) {
                SimpleDateFormat(applicationContext().getString(R.string.messages_dateformat_yesterday),
                        Locale.getDefault())
                        .format(date)
            } else {
                // todo proper relative dates
                SimpleDateFormat(applicationContext().getString(R.string.messages_dateformat_other),
                        Locale.getDefault())
                        .format(date)
            }

        }

        private fun getRecipientText(conversation: Conversation): String {
            val prefs = applicationContext()
                    .getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)

            var text = applicationContext().getString(when {
                conversation.originalSenderId == prefs.getString("userid", "") &&
                        // From self and only one recipient
                        conversation.recipientCount == 1 -> R.string.messages_partic_fromself
                conversation.originalSenderId == prefs.getString("userid", "") ->
                    // From self with multiple recipients
                    R.string.messages_partic_fromself_more
                conversation.recipientCount == 1 ->
                    // Not from self, only one recipient
                    R.string.messages_partic_toself
                else ->
                    // Not from self and multiple recipients
                    R.string.messages_partic_toself_more
            })

            // Replace placeholders
            text = text.replace("%sender", UsersDb.getName(conversation.originalSenderId))
                    .replace("%countall", conversation.recipientCount.toString())
                    .replace("%count", (conversation.recipientCount - 1).toString())
                    .replace("%recipient",
                            conversation.firstMessage?.recipients?.getOrNull(0).toString())

            return text
        }

    }

    // Creates new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.item_conversation, viewGroup, false)
        return ViewHolder(view, onClick, onLongClick)
    }

    // Replaces the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // Bind data to ViewHolder
        viewHolder.bind(conversations[position], selectedItems.contains(position), themeColor)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = conversations.size

    fun getSelected() = selectedItems.map { conversations[it] }

    fun clearSelected() {
        selectedItems.clear()
        selectMode = false
        onSelectModeChange(false)
    }
}