package joshua_luo.example.cmpt362projectmanhunt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class MatchHistoryItem(val gameId: String, val winner: String, val role: String, val duration: Double)
class HistoryAdapter(
    private val items: MutableList<MatchHistoryItem> = mutableListOf()
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val gameIdText: TextView = itemView.findViewById(R.id.game_id)
        val winnerText: TextView = itemView.findViewById(R.id.match_winner)
        val roleText: TextView = itemView.findViewById(R.id.your_role)
        val durationText: TextView = itemView.findViewById(R.id.match_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_match_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        holder.gameIdText.text = item.gameId
        holder.winnerText.text = "Winner: ${item.winner}"
        holder.roleText.text = "Your Role: ${item.role}"
        holder.durationText.text = "${item.duration} min"
    }

    override fun getItemCount(): Int = items.size

    fun update(newItem: List<MatchHistoryItem>) {
        items.clear()
        items.addAll(newItem)
        notifyDataSetChanged()
    }
}
