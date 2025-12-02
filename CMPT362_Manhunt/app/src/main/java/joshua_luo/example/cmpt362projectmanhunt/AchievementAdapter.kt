package joshua_luo.example.cmpt362projectmanhunt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AchievementItem(val achievement: String)

class AchievementAdapter(private val items: List<String>):
    RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder>() {
    class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val achievementText: TextView = itemView.findViewById(R.id.achievement_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.achievementText.text = items[position]
    }

    override fun getItemCount(): Int {
        return items.size
    }
}