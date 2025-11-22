package joshua_luo.example.cmpt362projectmanhunt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

data class MemberLoc(val lat: Double, val lon: Double, val ts: Long)
data class Member(val userId: String, val name: String?, val loc: MemberLoc?, val updatedAt: Long)

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
        h.sub.text = if (m.loc != null) "lat=%.5f, lon=%.5f".format(m.loc.lat, m.loc.lon) else "No location yet"
    }
}
