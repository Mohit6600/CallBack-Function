package com.hushbunny.app.ui.moment

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.hushbunny.app.R
import com.hushbunny.app.databinding.ItemBookmarkBinding
import com.hushbunny.app.databinding.PopupReactionBinding
import com.hushbunny.app.providers.ResourceProvider
import com.hushbunny.app.ui.comment.CommentListAdapter
import com.hushbunny.app.ui.enumclass.ReactionPageName
import com.hushbunny.app.ui.enumclass.getImage
import com.hushbunny.app.ui.enumclass.reactionText
import com.hushbunny.app.ui.model.CommentModel
import com.hushbunny.app.ui.model.MomentKidsModel
import com.hushbunny.app.ui.model.MomentListingModel
import com.hushbunny.app.uitls.AppConstants
import com.hushbunny.app.uitls.AppConstants.Companion.TAG
import com.hushbunny.app.uitls.BaseListAdapter
import com.hushbunny.app.uitls.DateFormatUtils.convertISODateIntoAppDateFormat
import com.hushbunny.app.uitls.DateFormatUtils.getTimeAgo
import com.hushbunny.app.uitls.ImageViewAndFileUtils.loadCircleImageFromURL
import com.hushbunny.app.uitls.prependZeroToStringIfSingleDigit
import com.hushbunny.app.uitls.toIntOrZero
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


class MomentAdapter(
    private val isOtherUser: Boolean = false,
    private val isKidsProfile: Boolean = false,
    private val resourceProvider: ResourceProvider,
    private val onItemClick: ((View, Int, String, MomentListingModel) -> Unit)? = null,
    private val onCommentClick: ((Int, String, String, String, MomentListingModel) -> Unit)? = null,
    private val onMediaClick: ((Int,String, String) -> Unit)? = null,
    private val onKidClick: ((MomentListingModel, MomentKidsModel) -> Unit)? = null
) : BaseListAdapter<MomentListingModel, ItemBookmarkBinding>(ItemDiffCallback()) {

    private var totalMomentCount: Int = 0
    companion object{
        var itemPosition =0
    }

    override fun createBinding(parent: ViewGroup): ItemBookmarkBinding {
        return ItemBookmarkBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    }

    fun clearGlideMemoryCache(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            Glide.get(context).clearMemory()
            withContext(Dispatchers.IO) {
                Glide.get(context).clearDiskCache()
            }

        }

    }

    override fun bind(binding: ItemBookmarkBinding, item: MomentListingModel, position: Int) {
        if (item.isImportant == true) binding.starImage.setImageResource(R.drawable.ic_important_marked)
        else binding.starImage.setImageResource(R.drawable.ic_setting_important)
        if (item.isBookmarked == true) binding.bookmarkImage.setImageResource(R.drawable.ic_setting_book_mark)
        else binding.bookmarkImage.setImageResource(R.drawable.ic_book_mark)
        binding.dateText.text = item.createdAt?.getTimeAgo()
        val convertedDate = item.momentDate?.convertISODateIntoAppDateFormat()
        binding.dateValueText.text = convertedDate
        binding.dateButton.text = convertedDate
        binding.userNameText.text = item.addedBy?.name.orEmpty()

        val coun = totalMomentCount.minus(position)
        var finalCount = coun.toString()
        if (coun in 1..9)
            finalCount = "0$finalCount"

        binding.momentCountText.text = finalCount
        binding.favoriteCountText.text = item.reactionCount.orEmpty()
        val commentCount = item.commentCount
        if (commentCount.orEmpty().isNotEmpty() && commentCount?.toInt()!! > 0) {
            binding.commentCountText.text = String.format("%02d", commentCount.toInt())
        } else {
            binding.commentCountText.text = commentCount.orEmpty()
        }
        binding.lessDescriptionText.text = item.description.orEmpty()
        binding.moreDescriptionText.text = item.description.orEmpty()
        binding.dateGroup.visibility = if (isKidsProfile) getDateVisibility(position) else View.GONE
        if (item.addedBy?._id.orEmpty() == AppConstants.getUserID() || item.isAddedBySpouse == true)
            binding.moreImage.visibility = View.VISIBLE
        else binding.moreImage.visibility = View.INVISIBLE
        if (item.isReacted == true) {
            when (item.reactedInfo?.emojiType) {
                ReactionPageName.LAUGH.name -> {
                    binding.favoriteImage.setImageResource(R.drawable.ic_laugh_reaction)
                }

                ReactionPageName.SAD.name -> {
                    binding.favoriteImage.setImageResource(R.drawable.ic_sad_reaction)
                }

                else -> {
                    binding.favoriteImage.setImageResource(R.drawable.ic_heart_reaction)
                }
            }
        } else {
            binding.favoriteImage.setImageResource(R.drawable.ic_favorite)
        }
        if (item.addedBy?.image.isNullOrEmpty())
            binding.userImage.setImageDrawable(
                ContextCompat.getDrawable(
                    binding.userImage.context,
                    R.drawable.ic_no_kid_icon
                )
            )
        else binding.userImage.loadCircleImageFromURL(item.addedBy?.image.orEmpty())
        if (item.description.orEmpty().length > 800)
            binding.readMoreText.visibility = View.VISIBLE
        else binding.readMoreText.visibility = View.GONE
        binding.readMoreText.setOnClickListener {
            binding.moreDescriptionText.visibility = View.VISIBLE
            binding.lessDescriptionText.visibility = View.GONE
            binding.readMoreText.visibility = View.GONE
//            if (binding.readMoreText.text.toString() == binding.readMoreText.context.getString(R.string.read_more)) {

            //binding.readMoreText.text = binding.readMoreText.context.getString(R.string.view_less)
//            } else {
//                binding.moreDescriptionText.visibility = View.GONE
//                binding.lessDescriptionText.visibility = View.VISIBLE
//                binding.readMoreText.text = binding.readMoreText.context.getString(R.string.read_more)
//            }
        }
        val kidsAdapter = MomentKidsListAdapter(onItemClick = {
            onKidClick?.invoke(item, it)
        })
        binding.kidsList.adapter = kidsAdapter
        kidsAdapter.submitList(item.kidId)

        val imageAdapter = ImageSliderAdapter(
            resourceProvider = resourceProvider,
            imageList = item.mediaContent.orEmpty(),
            context = binding.headerImageViewPager.context,
            onMediaClick = { type: String, url: String, isLocal: Boolean ->
                Log.d(TAG, "bind: ${item.mediaContent?.size}")
                if ((item.mediaContent?.size ?: 1) > 1) {
                    val gson = Gson()
                    val myArrayAsString = gson.toJson(item.mediaContent)
                    itemPosition = binding.headerImageViewPager.currentItem
                    onMediaClick?.invoke(position,"SLIDER", myArrayAsString)
                } else

                    onMediaClick?.invoke(position,type, url)
            }
        )
        binding.headerImageViewPager.offscreenPageLimit = item.mediaContent.orEmpty().size
        binding.headerImageViewPager.adapter = imageAdapter
        imageAdapter.handleViewpager(binding.headerImageViewPager)

        binding.headerImageViewPager.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->

        }
        if (item.mediaContent.orEmpty().size > 1) {
            binding.headerImageViewPager.clipToPadding = false
            binding.headerImageViewPager.setPadding(
                0,
                0,
                0,
                0
            )
            binding.headerImageViewPager.pageMargin =
                0

        } else {
            binding.headerImageViewPager.setPadding(0, 0, 0, 0)
            binding.headerImageViewPager.pageMargin = 0
        }
        if (item.mediaContent.isNullOrEmpty())
            binding.headerImageViewPager.visibility = View.GONE
        else binding.headerImageViewPager.visibility = View.VISIBLE

        val commentAdapter = CommentListAdapter(
            onDeleteClick = { pos: Int, commentModel: CommentModel ->
                onCommentClick?.invoke(
                    pos,
                    AppConstants.COMMENT_DELETE,
                    commentModel._id.orEmpty(),
                    commentModel.commentBy?.userRelation.orEmpty(),
                    item
                )
            },
            onReportClick = { pos: Int, commentModel: CommentModel ->
                onCommentClick?.invoke(
                    pos,
                    AppConstants.COMMENT_REPORT,
                    commentModel._id.orEmpty(),
                    commentModel.commentBy?.userRelation.orEmpty(),
                    item
                )
            },
            onUserClick = {
                onCommentClick?.invoke(
                    position,
                    AppConstants.USER_PROFILE,
                    it.commentBy?._id.orEmpty(),
                    it.commentBy?.userRelation.orEmpty(),
                    item
                )
            }
        )
        binding.commentList.adapter = commentAdapter
        commentAdapter.submitList(AppConstants.createCommentList(item))
        binding.viewAllCommentText.run {
            visibility = if (commentCount.toIntOrZero() > 2) View.VISIBLE else View.GONE
            text = binding.viewAllCommentText.context.getString(
                R.string.view_all_comment,
                commentCount.prependZeroToStringIfSingleDigit()
            )
        }
        if (item.comments.isNullOrEmpty()) {
            binding.actionView.visibility = View.INVISIBLE
            binding.bottomSpace.visibility = View.GONE
            binding.commentsContainer.visibility = View.GONE
        } else {
            binding.actionView.visibility = View.VISIBLE
            binding.bottomSpace.visibility = View.VISIBLE
            binding.commentsContainer.visibility = View.VISIBLE
        }
        binding.starImage.setOnClickListener { view ->
            onItemClick!!.invoke(
                view,
                position,
                binding.starImage.context.getString(R.string.mark_as_important_moment),
                item
            )
        }
        binding.favoriteImage.setOnClickListener {
            val popupReactionBinding = PopupReactionBinding.inflate(LayoutInflater.from(it.context))
            val popupWindow =
                PopupWindow(
                    popupReactionBinding.root,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                )
            popupWindow.isOutsideTouchable = true
            val leftToRight = resourceProvider.getDimension(R.dimen.margin_17).toInt()
            val bottomToTop = resourceProvider.getDimension(R.dimen.margin_70).toInt()
            popupWindow.showAsDropDown(binding.favoriteImage, leftToRight, -bottomToTop)
            popupReactionBinding.heartImage.setOnClickListener { view ->
                onItemClick?.invoke(view, position, ReactionPageName.HEART.name, item)
                popupWindow.dismiss()
            }
            popupReactionBinding.laughImage.setOnClickListener { view ->
                onItemClick?.invoke(view, position, ReactionPageName.LAUGH.name, item)
                popupWindow.dismiss()
            }
            popupReactionBinding.sadImage.setOnClickListener { view ->
                onItemClick?.invoke(view, position, ReactionPageName.SAD.name, item)
                popupWindow.dismiss()
            }
        }
        binding.commentImage.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.comments),
                item
            )
        }
        binding.commentCountText.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.comments),
                item
            )
        }
        binding.favoriteCountText.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.like),
                item
            )
        }
        binding.viewAllCommentText.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.comments),
                item
            )
        }
        binding.shareImage.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.share),
                item
            )
        }
        binding.bookmarkImage.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.bookmarks),
                item
            )
        }
        binding.userImage.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.user_detail),
                item
            )
        }
        binding.userNameText.setOnClickListener { view ->
            onItemClick?.invoke(
                view,
                position,
                binding.bookmarkImage.context.getString(R.string.user_detail),
                item
            )
        }
        binding.moreImage.setOnClickListener { view ->
            val popUp = PopupMenu(binding.moreImage.context, view)
            if (item.isAddedBySpouse == true)
                popUp.menuInflater.inflate(R.menu.spouse_menu, popUp.menu)
            else if (item.addedBy?._id.orEmpty() == AppConstants.getUserID())
                popUp.menuInflater.inflate(R.menu.user_menu, popUp.menu)
            else popUp.menuInflater.inflate(R.menu.other_user_menu, popUp.menu)
            if (item.isAddedBySpouse == true || item.addedBy?._id.orEmpty() == AppConstants.getUserID())
                popUp.menu.findItem(R.id.actionMarkImportant).title =
                    if (item.isImportant == true) binding.moreImage.context.getString(R.string.un_mark_from_important_moment) else binding.moreImage.context.getString(
                        R.string.mark_as_important_moment
                    )
            val deleteMomentMenuItem = popUp.menu.findItem(R.id.deleteMoment)
            deleteMomentMenuItem?.let {
                val title = it.title
                if (!title.isNullOrEmpty()) {
                    val coloredTitle = SpannableString(title)
                    coloredTitle.setSpan(
                        ForegroundColorSpan(
                            ContextCompat.getColor(
                                binding.moreImage.context,
                                R.color.red
                            )
                        ),
                        0,
                        coloredTitle.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    it.title = coloredTitle
                }
            }
            popUp.show()
            popUp.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.actionEditMoment -> {
                        onItemClick?.invoke(view, position, AppConstants.MOMENT_EDIT, item)
                    }
//                    R.id.actionAddYourKid -> {
//                        onItemClick?.invoke(view, position, AppConstants.ADD_YOUR_KID, item)
//                    }
                    R.id.actionMarkImportant -> {
                        onItemClick?.invoke(view, position, AppConstants.IMPORTANT_MOMENT, item)
                    }

                    R.id.actionCopyUrl -> {
                        onItemClick?.invoke(view, position, AppConstants.COPY_URL, item)
                    }
//                    R.id.actionBlock -> {
//                        onItemClick?.invoke(view, position, APIConstants.BLOCKED, item)
//                    }
                    R.id.actionReport -> {
                        onItemClick?.invoke(view, position, AppConstants.MOMENT_REPORT, item)
                    }

                    R.id.deleteMoment -> {
                        onItemClick?.invoke(view, position, AppConstants.DELETE_MOMENT, item)
                    }
                }
                true
            }
        }
        val dots = arrayOfNulls<ImageView>(item.mediaContent?.size ?: 0)
        binding.sliderDots.removeAllViews()
        if (dots.isNotEmpty() && dots.size > 1) {
            buildViewPagerSlidingDotPanels(
                binding.headerImageViewPager.context,
                binding.sliderDots,
                dots
            )
            setViewPagerPageChangeListener(
                binding.headerImageViewPager.context,
                binding.headerImageViewPager,
                dots
            )
        }

        if (item.otherUserReaction != null) {
            val otherUserName = item.otherUserReaction.reactedBy?.name.orEmpty()
            binding.reactionGroup.visibility =
                if (otherUserName.isNotEmpty()) View.VISIBLE else View.GONE
            binding.reactionUserName.text = otherUserName
            binding.reactionText.run {
                val emojiType = item.otherUserReaction.emojiType
                text = emojiType.reactionText()
                emojiType.getImage()?.let { resId ->
                    setCompoundDrawablesWithIntrinsicBounds(0, 0, resId, 0)
                }
            }
        } else if (item.otherUserComment != null) {
            val otherUserName = item.otherUserComment.commentBy?.name.orEmpty()
            binding.reactionGroup.visibility =
                if (otherUserName.isNotEmpty()) View.VISIBLE else View.GONE
            binding.reactionUserName.text = otherUserName
            binding.reactionText.text =
                binding.reactionText.context.getString(R.string.commented_on_this)
        } else {
            binding.reactionGroup.visibility = View.GONE
        }
    }

    private fun buildViewPagerSlidingDotPanels(
        context: Context,
        sliderDotsPanel: LinearLayout,
        dots: Array<ImageView?>
    ) {
        for (i in dots.indices) {
            dots[i] = ImageView(context)
            dots[i]?.background =
                ContextCompat.getDrawable(context, R.drawable.view_bordered_ripple)
            dots[i]?.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.slider_non_active_dot
                )
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            dots[i]?.setPadding(4, 12, 4, 8)
            sliderDotsPanel.addView(dots[i], params)
            dots[i]?.setOnClickListener {
            }
        }
        dots[0]?.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                R.drawable.slider_active_dot
            )
        )
    }

    private fun setViewPagerPageChangeListener(
        context: Context,
        productImageViewPager: ViewPager,
        dots: Array<ImageView?>
    ) {
        productImageViewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                for (imageView in dots) {
                    imageView?.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.slider_non_active_dot
                        )
                    )
                }
                dots.getOrNull(position)?.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.slider_active_dot
                    )
                )
            }
        })
    }

    fun updateMomentDetail(position: Int, model: MomentListingModel) {
        currentList[position].isBookmarked = model.isBookmarked
        currentList[position].isImportant = model.isImportant
        currentList[position].reactionCount = model.reactionCount
        currentList[position].isReacted = model.isReacted
        currentList[position].reactedInfo = model.reactedInfo
        notifyItemChanged(position)
    }

    fun updateDeletedMoment(position: Int) {
        val newList = arrayListOf<MomentListingModel>().apply {
            addAll(currentList)
            removeAt(position)
        }
        submitList(newList)
    }

    fun setTotalMomentCount(count: Int?) {
        totalMomentCount = count ?: 0
    }

    private fun getDateVisibility(position: Int): Int {
        return if (position == 0) View.VISIBLE
        else {
            val previousDate = currentList[position - 1]
            val currentDate = currentList[position]
            if (previousDate.momentDate?.convertISODateIntoAppDateFormat() == currentDate.momentDate?.convertISODateIntoAppDateFormat()) {
                View.GONE
            } else View.VISIBLE
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<MomentListingModel>() {
        override fun areItemsTheSame(
            oldItem: MomentListingModel,
            newItem: MomentListingModel
        ): Boolean {
            return oldItem._id == newItem._id
        }

        override fun areContentsTheSame(
            oldItem: MomentListingModel,
            newItem: MomentListingModel
        ): Boolean {
            return oldItem == newItem
        }
    }

}