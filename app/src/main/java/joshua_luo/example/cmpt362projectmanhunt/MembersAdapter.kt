package joshua_luo.example.cmpt362projectmanhunt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

data class MemberLoc(val lat: Double, val lon: Double, val ts: Long)
data class MemberAbility(val id: String, val ts: Long)
data class MemberStatus(val team: String?, val role: String?, val health: Int?)
data class Member(
    val userId: String,
    val name: String?,
    val loc: MemberLoc?,
    val updatedAt: Long,
    val abilities: List<MemberAbility>,
    val status: MemberStatus?
)

class MembersAdapter : ListAdapter<Member, MembersAdapter.VH>(D) {
    object D : DiffUtil.ItemCallback<Member>() {
        override fun areItemsTheSame(o: Member, n: Member) = o.userId == n.userId
        override fun areContentsTheSame(o: Member, n: Member) = o == n
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val sub: TextView = v.findViewById(R.id.tvSub)
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
        val view = LayoutInflater.from(p.context).inflate(R.layout.item_member, p, false)
        return VH(view)
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val m = getItem(i)
        h.title.text = m.name ?: m.userId

        val locStr = if (m.loc != null) {
            "lat=%.5f, lon=%.5f".format(m.loc.lat, m.loc.lon)
        } else {
            "No location"
        }

        val statusStr = m.status?.let {
            val team = it.team ?: "-"
            val role = it.role ?: "-"
            val hp = it.health?.toString() ?: "-"
            "team=$team, role=$role, hp=$hp"
        } ?: "no status"

        val abilitiesStr = if (m.abilities.isNotEmpty()) {
            val last = m.abilities.maxBy { it.ts }
            val count = m.abilities.size
            "abilities=$count, last=${last.id}"
        } else {
            "no abilities"
        }

        h.sub.text = listOf(locStr, statusStr, abilitiesStr).joinToString(" | ")
    }
}
