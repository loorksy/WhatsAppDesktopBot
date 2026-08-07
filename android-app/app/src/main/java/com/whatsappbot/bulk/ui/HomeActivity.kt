package com.whatsappbot.bulk.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.databinding.ActivityHomeBinding
import com.whatsappbot.bulk.databinding.ItemToolBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tools = listOf(
            ToolItem(
                title = getString(R.string.tool_bulk_title),
                subtitle = getString(R.string.tool_bulk_sub),
                icon = "✉",
            ) {
                startActivity(Intent(this, BulkFormActivity::class.java))
            }
        )

        binding.recyclerTools.layoutManager = LinearLayoutManager(this)
        binding.recyclerTools.adapter = ToolAdapter(tools)
    }

    data class ToolItem(
        val title: String,
        val subtitle: String,
        val icon: String,
        val onClick: () -> Unit,
    )

    private class ToolAdapter(
        private val items: List<ToolItem>,
    ) : RecyclerView.Adapter<ToolAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        class VH(private val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: ToolItem) {
                binding.icon.text = item.icon
                binding.textTitle.text = item.title
                binding.textSub.text = item.subtitle
                binding.root.setOnClickListener { item.onClick() }
            }
        }
    }
}
