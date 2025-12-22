package com.example.garapro.ui.quotations

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garapro.R
import com.example.garapro.data.model.quotations.PartCategory
import com.example.garapro.data.model.quotations.QuotationServiceDetail
import com.example.garapro.data.model.quotations.ServicePromotionUiState
import com.example.garapro.databinding.ItemQuotationServiceBinding
import com.example.garapro.utils.MoneyUtils
import java.text.NumberFormat
import java.util.Locale

class QuotationServiceAdapter(
    private var services: List<QuotationServiceDetail>,
    private var onCheckChanged: (String, Boolean) -> Unit,
    private var onPartToggle: (String, String, String) -> Unit,
    private var onPromotionClick: (QuotationServiceDetail) -> Unit,
    private var isEditable: Boolean = true
) : RecyclerView.Adapter<QuotationServiceAdapter.ViewHolder>() {

    private val expandedStates = mutableMapOf<String, Boolean>()
    private var currentlyExpandedServiceId: String? = null
    private val initializedServices = mutableSetOf<String>()

    private var promotions: Map<String, ServicePromotionUiState> = emptyMap()

    fun updateEditable(editable: Boolean) {
        this.isEditable = editable
        notifyDataSetChanged()
    }

    fun updateOnCheckChanged(newOnCheckChanged: (String, Boolean) -> Unit) {
        this.onCheckChanged = newOnCheckChanged

    }

    fun updateServices(newServices: List<QuotationServiceDetail>) {
        services = newServices
        notifyDataSetChanged()
    }

    fun updatePromotions(newPromotions: Map<String, ServicePromotionUiState>) {
        promotions = newPromotions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemQuotationServiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(services[position])
    }

    override fun getItemCount() = services.size

    inner class ViewHolder(private val binding: ItemQuotationServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: QuotationServiceDetail) {
            binding.tvServiceName.text = service.serviceName
            binding.tvServiceDescription.text = service.serviceDescription ?: ""

            // Required chip
            binding.tvRequired.visibility = if (service.isRequired) View.VISIBLE else View.GONE
            binding.tvAdvance.visibility = if (service.isAdvanced) View.VISIBLE else View.GONE

            if (service.isGood) {
                // checkbox: disable & uncheck
                binding.cbService.setOnCheckedChangeListener(null)
                binding.cbService.isEnabled = false
                binding.cbService.isChecked = false

                // màu chữ service cho dễ nhận biết
                val green = ContextCompat.getColor(binding.root.context, R.color.green)
                binding.tvServiceName.setTextColor(green)

                // hiển thị chữ "Good" thay vì giá
                binding.tvServicePriceBase.visibility = View.VISIBLE
                binding.tvServicePriceBase.text = "Good"
                binding.tvServicePriceBase.setTextColor(green)

                // ẩn các view liên quan tới giá & khuyến mãi
                binding.tvServicePriceWithParts.visibility = View.GONE
                binding.tvServicePriceAfterPromotion.visibility = View.GONE
                binding.tvDiscountPromotion.visibility = View.GONE
                binding.materialDivider.visibility = View.GONE

                // button promotion: disable, text Good
                binding.btnPromotion.visibility = View.GONE
                binding.btnPromotion.isEnabled = false
                binding.btnPromotion.alpha = 0.7f
                binding.btnPromotion.text = "Good"
                binding.tvPromotionName.visibility = View.GONE

                // ẩn phần parts
                binding.rvPartCategories.visibility = View.GONE
                binding.btnToggleParts.visibility = View.GONE
                binding.selectedPartsSummary.visibility = View.GONE

                return
            }


            // ====== Default selection when in edit mode ======
            if (isEditable && !initializedServices.contains(service.quotationServiceId)) {
                initializedServices.add(service.quotationServiceId)

                if (!service.isSelected && !service.isGood) {

                    binding.root.post {

                        onCheckChanged(service.quotationServiceId, true)


                        service.partCategories.forEach { category ->
                            val hasSelectedPart = category.parts.any { it.isSelected }
                            if (!hasSelectedPart && category.parts.isNotEmpty()) {
                                val firstPart = category.parts.first()
                                onPartToggle(
                                    service.quotationServiceId,
                                    category.partCategoryId,
                                    firstPart.quotationServicePartId
                                )
                            }
                        }
                    }
                }
            }


            // Checkbox logic
            val canToggleService = if (service.isRequired) {
                isEditable && !service.isSelected
            } else {
                isEditable
            }

            binding.cbService.isEnabled = canToggleService
            binding.cbService.setOnCheckedChangeListener(null)
            binding.cbService.isChecked = service.isSelected

            if (isEditable && canToggleService) {
                binding.cbService.setOnCheckedChangeListener { _, isChecked ->
                    onCheckChanged(service.quotationServiceId, isChecked)
                }
            }

            // ====== Prices: base, with parts, after promotion ======
            bindPrices(service)

            // ====== Promotion button & text ======
            bindPromotionViews(service)

            // ====== Parts expand/collapse ======
            if (!service.isSelected) {
                expandedStates.remove(service.quotationServiceId)
                binding.rvPartCategories.visibility = View.GONE
                binding.btnToggleParts.visibility = View.GONE
                binding.selectedPartsSummary.visibility = View.GONE
            } else {
                binding.btnToggleParts.visibility = View.VISIBLE
                val isExpanded = expandedStates[service.quotationServiceId] ?: false
                setupPartCategoriesVisibility(service, isExpanded)
            }

            binding.btnToggleParts.setOnClickListener {
                togglePartCategories(service)
            }
        }

        private fun bindPrices(service: QuotationServiceDetail) {
            val partsTotal = service.partCategories
                .flatMap { it.parts }
                .filter { it.isSelected }
                .sumOf { it.price * it.quantity }

            val basePrice = service.price              // base service price
            val priceWithParts = basePrice + partsTotal      // service + parts

            // 1) Always show base service price
            binding.tvServicePriceBase.visibility = View.VISIBLE
            binding.tvServicePriceBase.text =
                "Service: ${formatCurrency(basePrice)}"

            // 2) Service + selected parts
            if (partsTotal > 0.0) {
                binding.tvServicePriceWithParts.visibility = View.VISIBLE
                binding.tvServicePriceWithParts.text =
                    "Parts:+${formatCurrency(partsTotal)}"
            } else {
                binding.tvServicePriceWithParts.visibility = View.GONE
            }

            // 3) After promotion
            val promoState = promotions[service.serviceId]
            val hasServerPromotion =
                !service.appliedPromotionId.isNullOrBlank() &&
                        service.appliedPromotion != null &&
                        (service.finalPrice ?: 0.0) > 0.0

            if (isEditable) {
                // Editable (Sent) – use client-side promotion state
                if (promoState != null &&
                    promoState.selectedPromotion != null &&
                    promoState.finalPrice < priceWithParts
                ) {
                    binding.tvDiscountPromotion.visibility = View.VISIBLE
                    binding.tvDiscountPromotion.text = "Discount:-${formatCurrency(promoState.selectedPromotion.calculatedDiscount)}"
                    binding.tvServicePriceAfterPromotion.visibility = View.VISIBLE
                    binding.tvServicePriceAfterPromotion.text =
                        "${formatCurrency(promoState.finalPrice)}"
                    binding.materialDivider.visibility =View.VISIBLE

                } else {
                    binding.tvServicePriceAfterPromotion.visibility = View.GONE
                    binding.materialDivider.visibility =View.GONE
                }
            } else {
                // View-only (Approved/Rejected/Expired/Pending) – use server data
                if (hasServerPromotion) {
//                    val finalServicePrice = service.finalPrice ?: basePrice
                    val finalTotal = service.finalPrice?: 0.0

                    binding.tvDiscountPromotion.visibility = View.VISIBLE
                    binding.tvDiscountPromotion.text =
                        "Discount:-${formatCurrency(service.discountValue ?: 0.0)}"
                    binding.tvServicePriceAfterPromotion.visibility = View.VISIBLE
                    binding.tvServicePriceAfterPromotion.text =
                        " ${formatCurrency(finalTotal)}"

                    binding.materialDivider.visibility =View.VISIBLE

                } else {
                    binding.tvServicePriceAfterPromotion.visibility = View.GONE
                    binding.materialDivider.visibility =View.GONE
                }
            }
        }

        private fun bindPromotionViews(service: QuotationServiceDetail) {
            val promoState = promotions[service.serviceId]
            val serverPromotion = service.appliedPromotion
            val hasServerPromotion =
                !service.appliedPromotionId.isNullOrBlank() && serverPromotion != null

            if (isEditable && service.isSelected) {
                // Editable mode: user can open bottom sheet
                binding.btnPromotion.isEnabled = true
                binding.btnPromotion.alpha = 1f
                binding.btnPromotion.setOnClickListener { onPromotionClick(service) }

                // Button text
                binding.btnPromotion.text = if (promoState?.selectedPromotion != null) {
                    "Change promotion(${promoState.selectedPromotion.name}(-${MoneyUtils.formatVietnameseCurrency(promoState.selectedPromotion.calculatedDiscount)}))"
                } else {
                    "Choose promotion"
                }

                // Small label under prices
                if (promoState?.selectedPromotion != null) {
                    binding.tvPromotionName.visibility = View.VISIBLE
                    binding.tvPromotionName.text =
                        "Promotion: ${promoState.selectedPromotion.name}"
                } else {
                    binding.tvPromotionName.visibility = View.GONE
                }

            } else {
                // View-only mode: show applied promotion from server, do not open bottom sheet
                binding.btnPromotion.isEnabled = false
                binding.btnPromotion.alpha = 0.7f
                binding.btnPromotion.setOnClickListener(null)

                if (hasServerPromotion) {
                    val discountDisplay = service.discountValue ?: 0.0
                    val promoName = serverPromotion?.name ?: "Promotion"

                    // Example: "Grand Opening Discount (-100.000 ₫)"
                    binding.btnPromotion.text =
                        "$promoName (-${formatCurrency(discountDisplay)})"

                    binding.tvPromotionName.visibility = View.VISIBLE
                    binding.tvPromotionName.text =
                        "Promotion: ${serverPromotion?.name}"
                } else {
                    // No promotion
                    binding.btnPromotion.text = "Promotion: (Nothing)"
                    binding.tvPromotionName.visibility = View.GONE
                }
            }
        }

        private fun setupPartCategoriesVisibility(
            service: QuotationServiceDetail,
            isExpanded: Boolean
        ) {
            if (isExpanded) {
                binding.rvPartCategories.visibility = View.VISIBLE
                binding.selectedPartsSummary.visibility = View.GONE
                binding.btnToggleParts.text = "Hide parts"
                binding.btnToggleParts.setIconResource(R.drawable.ic_arrow_drop_up_24dp)

                setupPartCategories(service.partCategories, service)
            } else {
                binding.rvPartCategories.visibility = View.GONE
                binding.selectedPartsSummary.visibility = View.VISIBLE
                binding.btnToggleParts.text = "Show parts"
                binding.btnToggleParts.setIconResource(R.drawable.ic_arrow_drop_down_24dp)

                setupSelectedPartsSummary(service)
            }
        }

        private fun togglePartCategories(service: QuotationServiceDetail) {
            val serviceId = service.quotationServiceId
            val currentState = expandedStates[serviceId] ?: false

            if (!currentState && currentlyExpandedServiceId != null) {
                expandedStates[currentlyExpandedServiceId!!] = false
                notifyItemChanged(
                    services.indexOfFirst { it.quotationServiceId == currentlyExpandedServiceId }
                )
            }

            expandedStates[serviceId] = !currentState
            currentlyExpandedServiceId = if (!currentState) serviceId else null

            setupPartCategoriesVisibility(service, !currentState)
        }

        private fun setupSelectedPartsSummary(service: QuotationServiceDetail) {
            val selectedParts = service.partCategories
                .flatMap { it.parts }
                .filter { it.isSelected }

            if (selectedParts.isNotEmpty()) {
                binding.selectedPartsSummary.visibility = View.VISIBLE
                binding.tvSelectedParts.text =
                    selectedParts.joinToString(", ") { it.partName }
            } else {
                binding.selectedPartsSummary.visibility = View.GONE
            }
        }

        private fun setupPartCategories(
            partCategories: List<PartCategory>,
            service: QuotationServiceDetail
        ) {
            if (partCategories.isNotEmpty()) {
                val adapter = PartCategoryAdapter(
                    partCategories,
                    service,
                    onPartToggle,
                    isEditable
                )
                binding.rvPartCategories.adapter = adapter
                binding.rvPartCategories.layoutManager =
                    LinearLayoutManager(binding.root.context)
            }
        }

        private fun formatCurrency(amount: Double): String =
            NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(amount)
    }
}

