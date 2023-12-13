package com.hushbunny.app.ui.home

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.SimpleItemAnimator
import com.hushbunny.app.R
import com.hushbunny.app.core.HomeSharedViewModel
import com.hushbunny.app.databinding.FragmentHomeBinding
import com.hushbunny.app.di.AppComponentProvider
import com.hushbunny.app.providers.ResourceProvider
import com.hushbunny.app.ui.BaseActivity
import com.hushbunny.app.ui.enumclass.MediaType
import com.hushbunny.app.ui.enumclass.MomentDateType
import com.hushbunny.app.ui.enumclass.MomentType
import com.hushbunny.app.ui.enumclass.NotificationType
import com.hushbunny.app.ui.enumclass.ReactionPageName
import com.hushbunny.app.ui.enumclass.ReportType
import com.hushbunny.app.ui.model.MomentListingModel
import com.hushbunny.app.ui.moment.AddMomentViewModel
import com.hushbunny.app.ui.moment.MomentAdapter
import com.hushbunny.app.ui.notifications.NotificationsFragmentDirections
import com.hushbunny.app.ui.repository.FileUploadRepository
import com.hushbunny.app.ui.repository.HomeRepository
import com.hushbunny.app.ui.repository.MomentRepository
import com.hushbunny.app.ui.repository.UserActionRepository
import com.hushbunny.app.ui.sealedclass.BookMarkResponseInfo
import com.hushbunny.app.ui.sealedclass.CommentDeletedResponseInfo
import com.hushbunny.app.ui.sealedclass.KidsStatusInfo
import com.hushbunny.app.ui.sealedclass.MomentDeletedResponseInfo
import com.hushbunny.app.ui.sealedclass.MomentResponseInfo
import com.hushbunny.app.ui.setting.SettingActionDialog
import com.hushbunny.app.ui.setting.SettingViewModel
import com.hushbunny.app.ui.test.MyFragment
import com.hushbunny.app.ui.test.MyFragmentDirections
import com.hushbunny.app.uitls.APIConstants
import com.hushbunny.app.uitls.AppConstants
import com.hushbunny.app.uitls.PrefsManager
import com.hushbunny.app.uitls.dialog.DialogUtils
import com.hushbunny.app.uitls.enforceSingleScrollDirection
import com.hushbunny.app.uitls.toIntOrZero
import com.hushbunny.app.uitls.viewModelBuilderActivityScope
import com.hushbunny.app.uitls.viewModelBuilderFragmentScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var binding: FragmentHomeBinding? = null

    //    private val binding get() = _binding!!
    private lateinit var kidsAdapter: KidsAdapter
    private lateinit var momentAdapter: MomentAdapter
    private var momentList = ArrayList<MomentListingModel>()
    private var isLoading = true
    var currentPage = 1
    private var momentID = ""
    private var kidID = ""
    private var notificationType = ""

    @Inject
    lateinit var homeRepository: HomeRepository

    @Inject
    lateinit var resourceProvider: ResourceProvider

    @Inject
    lateinit var momentRepository: MomentRepository

    @Inject
    lateinit var fileUploadRepository: FileUploadRepository

    @Inject
    lateinit var userActionRepository: UserActionRepository

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (requireActivity().application as AppComponentProvider).getAppComponent().inject(this)
    }

    private val homeSharedViewModel: HomeSharedViewModel by viewModelBuilderActivityScope {
        HomeSharedViewModel()
    }

    private val settingViewModel: SettingViewModel by viewModelBuilderFragmentScope {
        SettingViewModel(
            ioDispatcher = Dispatchers.IO,
            resourceProvider = resourceProvider,
            userActionRepository = userActionRepository
        )
    }

    private val homeViewModel: HomeViewModel by viewModelBuilderActivityScope {
        HomeViewModel(
            ioDispatcher = Dispatchers.IO,
            resourceProvider = resourceProvider,
            homeRepository = homeRepository
        )
    }

    private val momentViewModel: AddMomentViewModel by viewModelBuilderFragmentScope {
        AddMomentViewModel(
            ioDispatcher = Dispatchers.IO,
            resourceProvider = resourceProvider,
            momentRepository = momentRepository,
            fileUploadRepository = fileUploadRepository
        )
    }

//    private val momentsViewModel: AddMomentViewModel by activityViewModels {  }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        momentID = activity?.intent?.getStringExtra(APIConstants.QUERY_PARAMS_MOMENT_ID).orEmpty()
        kidID = activity?.intent?.getStringExtra(APIConstants.QUERY_PARAMS_KID_ID).orEmpty()
        notificationType =
            activity?.intent?.getStringExtra(AppConstants.NOTIFICATION_TYPE).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (binding == null) {
            binding = FragmentHomeBinding.inflate(inflater, container, false)

            currentPage = 1
            momentList.clear()
            handleIntentLink()
            setAdapter()
            updateDeviceToken()
            getKidsList()
            getMomentList(true)
            setObserver()
            initClickListener()
        } else {
            setObserver()
        }
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    private fun updateDeviceToken() {
        if (PrefsManager.get().getBoolean(AppConstants.IS_REQUIRED_UPDATE_TOKEN, false)) {
            settingViewModel.updateDeviceToken()
            PrefsManager.get().saveBooleanValues(AppConstants.IS_REQUIRED_UPDATE_TOKEN, false)
        }
    }

    private fun handleIntentLink() {
        if (PrefsManager.get().getBoolean(AppConstants.IS_FROM_PUSH_NOTIFICATION, false)) {
            handlePushNotification()
            PrefsManager.get().saveBooleanValues(AppConstants.IS_FROM_PUSH_NOTIFICATION, false)
        } else if (momentID.isNotEmpty() && PrefsManager.get()
                .getBoolean(AppConstants.IS_REQUIRED_NAVIGATION, false)
        ) {
            findNavController().navigate(MyFragmentDirections.actionMomentDetailFragment(momentID))
        } else if (PrefsManager.get().getBoolean(AppConstants.GALLERY_MEDIA, false)) {
            findNavController().navigate(MyFragmentDirections.actionAddMomentFragment())
        }

    }

    private fun handlePushNotification() {
        when (notificationType) {
            NotificationType.LAUGH_REACTION_ON_MOMENT.name, NotificationType.LOVE_REACTION_ON_MOMENT.name, NotificationType.SAD_REACTION_ON_MOMENT.name, NotificationType.COMMENT_ON_MOMENT.name, NotificationType.PARENT_ADDED_MOMENT.name, NotificationType.PARENT_MARKED_MOMENT_IMPORTANT.name, NotificationType.MOMENT_REPORTED_RESOLVED.name, NotificationType.MOMENT_REPORTED_INTIMATION.name, NotificationType.REMINDER_MARK_MOMENT_IMPORTANT.name -> {
                findNavController().navigate(
                    NotificationsFragmentDirections.actionMomentDetailFragment(
                        momentID = momentID
                    )
                )
            }
            NotificationType.WELCOME_NOTIFICATION.name, NotificationType.ADD_KID.name, NotificationType.REMINDER_ADD_KID.name -> {
                findNavController().navigate(NotificationsFragmentDirections.actionAddKidFragment(isEditKid = false))
            }
//            NotificationType.OTHER_PARENT_INVITATION.name, NotificationType.REMINDER_OTHER_PARENT_INVITATION.name -> {
//                if (it.status.equals("ACCEPTED", true))
//                    findNavController().navigate(
//                        NotificationsFragmentDirections.actionKidsProfileFragment(
//                            kidId = it.kidID
//                        )
//                    )
//            }
            NotificationType.ADD_OTHER_PARENT.name, NotificationType.OTHER_PARENT_ACCEPTED_INVITATION.name, NotificationType.PARENT_DELETED_ACCOUNT.name, NotificationType.PARENT_DEACTIVATED_ACCOUNT.name, NotificationType.REMINDER_ADD_OTHER_PARENT.name, NotificationType.PARENT_REACTIVATED_ACCOUNT.name -> {
                findNavController().navigate(
                    NotificationsFragmentDirections.actionKidsProfileFragment(
                        kidId = kidID
                    )
                )
            }
            NotificationType.ADD_FIRST_MOMENT_BY_FIRST_PARENT.name, NotificationType.ADD_FIRST_MOMENT_BY_OTHER_PARENT.name, NotificationType.REMINDER_BOTH_PARENT_ADD_FIRST_MOMENT.name, NotificationType.REMINDER_ADD_MOMENT.name -> {
                findNavController().navigate(NotificationsFragmentDirections.actionAddMomentFragment())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (APIConstants.A) {
            APIConstants.A = false
            onPullToRefreshCalled()
        } else {
            var perPage = currentPage * APIConstants.QUERY_PARAMS_PER_PAGE_VALUE

            momentList.clear()
            currentPage = 1
            try {
                homeViewModel.getKidsList()
            } catch (e: Exception) {
            }
            getMomentList(false, perPage)
            currentPage = perPage / APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
            homeSharedViewModel.refreshNotificationUnReadCount()
        }
        (activity as? BaseActivity)?.setBottomNavigationVisibility(visibility = VISIBLE)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun initClickListener() {
        binding?.emptyViewContainer?.welcomeMessageText?.text =
            resourceProvider.getString(
                R.string.home_page_welcome_message,
                AppConstants.getUserName()
            )
        binding!!.pullRefresh.setOnRefreshListener {
            onPullToRefreshCalled()
        }
        binding!!.emptyViewContainer.addKidButton.setOnClickListener {
            findNavController().navigate(MyFragmentDirections.actionAddKidFragment(isEditKid = false))
        }
        binding?.emptyViewContainer?.addMomentButton!!.setOnClickListener {
            MyFragment.bottomNav!!.selectedItemId = R.id.addMomentFragment
            MyFragment.viewPager!!.setCurrentItem(2, false)
        }
        binding?.momentList?.isNestedScrollingEnabled = false
        binding?.kidsList?.enforceSingleScrollDirection()
        binding?.momentList?.enforceSingleScrollDirection()
//        ViewCompat.setNestedScrollingEnabled(binding?.momentList?, false)
//        binding?.scrollView?.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
//            val layoutManager = binding?.momentList?.layoutManager as LinearLayoutManager
//            val totalItemCount = layoutManager.itemCount - 1
//            val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
//            if (!isLoading && totalItemCount == lastVisibleItem && scrollY > oldScrollY) {
//                ++currentPage
//                getMomentList(false)
//            }
//        }
        binding?.scrollView?.viewTreeObserver?.addOnScrollChangedListener {
            val view =  binding?.scrollView?.getChildAt( binding?.scrollView?.childCount!! - 1)
            val diff = view!!.bottom - ( binding?.scrollView?.height!! +  binding?.scrollView?.scrollY!!)
            if (!isLoading && diff == 0) {
                binding?.noMoreDataText?.visibility = GONE
                binding?.progressLoading?.visibility = VISIBLE
                ++currentPage
                getMomentList(false)
            } else if(isLoading && diff == 0) {
                binding?.progressLoading?.visibility = GONE
                if(binding?.momentsShimmerContainer?.visibility == GONE && binding?.emptyViewContainer?.container?.visibility == GONE) {
                    binding?.noMoreDataText?.visibility = VISIBLE
                }
            }
        }
    }

    private fun onPullToRefreshCalled() {

        momentList.clear()
        currentPage = 1
        getMomentList(false)
        homeViewModel.getKidsList()

        homeSharedViewModel.refreshNotificationUnReadCount()

        Handler(Looper.getMainLooper()).postDelayed({
            binding!!.pullRefresh.isRefreshing = false

        }, Random.nextInt(300, 1000).toLong())

//        currentPage = 1
//        momentList.clear()
//        getKidsList()
//        getMomentList(true)
//        binding.pullRefresh.isRefreshing = false
//        homeSharedViewModel.refreshNotificationUnReadCount()
    }

    private fun setMomentListScrollBehaviour() {
        binding!!.kidsList.scrollToPosition(0)
        binding!!.scrollView.smoothScrollTo(0, 0)
        val isFirstItem = binding!!.scrollView.scrollY == 0
        if (isFirstItem) {
            onPullToRefreshCalled()
        }
    }

    private fun setAdapter() {
        kidsAdapter =
            KidsAdapter(resourceProvider = resourceProvider, isFromHome = true, addKidsClick = {
                APIConstants.A = false

                findNavController().navigate(MyFragmentDirections.actionAddKidFragment(isEditKid = false))
            }, kidsClick = {
                APIConstants.A = false

                findNavController().navigate(MyFragmentDirections.actionKidsProfileFragment(kidId = it._id.orEmpty()))
            }, addSpouseClick = { id, inviteInfoModel ->
                APIConstants.A = false

                findNavController().navigate(
                    MyFragmentDirections.actionInviteSpouseFragment(
                        kidId = id,
                        inviteInfo = inviteInfoModel
                    )
                )
            })
        binding!!.kidsList.adapter = kidsAdapter
        momentAdapter =
            MomentAdapter(
                resourceProvider = resourceProvider,
                onItemClick = { view: View, position: Int, type: String, item: MomentListingModel ->
                    when (type) {
                        resourceProvider.getString(R.string.bookmarks) -> {
                            if (item.isBookmarked == true) {
                                DialogUtils.showDialogWithCallBack(
                                    requireContext(),
                                    message = resourceProvider.getString(R.string.delete_from_bookmark),
                                    title = resourceProvider.getString(R.string.bookmarks),
                                    positiveButtonText = resourceProvider.getString(R.string.yes),
                                    negativeButtonText = resourceProvider.getString(R.string.cancel),
                                    positiveButtonCallback = {
                                        callBookMarkAPI(
                                            position = position,
                                            momentId = item._id.orEmpty()
                                        )
                                    }
                                )
                            } else {
                                callBookMarkAPI(position = position, momentId = item._id.orEmpty())
                            }
                        }
                        resourceProvider.getString(R.string.comments) -> {
                            val parentOne = item.parents?.firstOrNull()?._id.orEmpty()
                            val parentTwo = item.parents?.lastOrNull()?._id.orEmpty()
                            APIConstants.A = false

                            findNavController().navigate(
                                MyFragmentDirections.actionCommentFragment(
                                    momentID = item._id.orEmpty(),
                                    parentOneId = parentOne,
                                    parentTwoId = parentTwo
                                )
                            )
                        }
                        resourceProvider.getString(R.string.share) -> {
                            momentViewModel.shareMoment(item._id.orEmpty())
                            AppConstants.shareTheAPP(
                                requireActivity(),
                                title = resourceProvider.getString(R.string.share),
                                extraMessage = item.description.orEmpty(),
                                appUrl = item.shortLink.orEmpty()
                            )
                        }
                        resourceProvider.getString(R.string.like) -> {
                            findNavController().navigate(
                                MyFragmentDirections.actionReactionListFragment(
                                    momentID = item._id.orEmpty()
                                )
                            )
                        }
                        resourceProvider.getString(R.string.user_detail) -> {
                            var userRelationVal = item.addedBy?.userRelation.orEmpty()
                            if (userRelationVal == "SELF") {
                                AppConstants.IS_SHOW_BACK = true
                                findNavController().navigate(MyFragmentDirections.actionProfileFragment(isBackArrowEnabled = true))
                            } else if (userRelationVal == "OTHER_PARENT") {
                                findNavController().navigate(MyFragmentDirections.actionOtherUserProfileFragment(userID = item.addedBy?._id.orEmpty(), isOtherParent = true))
                            } else {
                                findNavController().navigate(MyFragmentDirections.actionOtherUserProfileFragment(userID = item.addedBy?._id.orEmpty(), isOtherParent = false))
                            }
                        }
                        APIConstants.BLOCKED -> {
                            binding!!.progressIndicator.showProgressbar()
                            homeViewModel.blockUser(
                                userId = item.addedBy?._id.orEmpty(),
                                action = APIConstants.BLOCKED
                            )
                        }
                        AppConstants.MOMENT_EDIT -> {
                            APIConstants.A = false

                            findNavController().navigate(
                                MyFragmentDirections.actionAddMomentFragment(
                                    isEdit = true,
                                    momentID = item._id.orEmpty()
                                )
                            )
                        }
                        AppConstants.ADD_YOUR_KID -> {
                            APIConstants.A = false

                            findNavController().navigate(
                                MyFragmentDirections.actionAddMomentFragment(
                                    isSpouseAdded = true,
                                    momentID = item._id.orEmpty()
                                )
                            )
                        }
                        AppConstants.IMPORTANT_MOMENT -> {
                            // binding.progressIndicator.showProgressbar()
                            momentViewModel.markMomentAsImportant(
                                position = position,
                                momentId = item._id.orEmpty()
                            )
                        }
                        AppConstants.MOMENT_REPORT -> {
                            findNavController().navigate(
                                MyFragmentDirections.actionReportFragment(
                                    type = ReportType.MOMENT.name,
                                    momentId = item._id.orEmpty()
                                )
                            )
                        }
                        AppConstants.COPY_URL -> {
                            AppConstants.copyURL(activity, item.shortLink)
                        }
                        ReactionPageName.LAUGH.name, ReactionPageName.SAD.name, ReactionPageName.HEART.name -> {
                            // binding.progressIndicator.showProgressbar()
                            momentViewModel.addReaction(
                                position = position,
                                emojiType = type,
                                momentId = item._id.orEmpty()
                            )
                        }
                        AppConstants.DELETE_MOMENT -> {
                            val dialog = SettingActionDialog(
                                requireContext(),
                                resourceProvider.getString(R.string.delete_moment)
                            ) {
                                binding!!.progressIndicator.showProgressbar()
                                momentViewModel.deleteMoment(
                                    position = position,
                                    momentId = item._id.orEmpty()
                                )
                            }
                            dialog.show()
                        }
                    }
                },
                onKidClick = { _, kidsModel ->
                    val loggedInUserId = AppConstants.getUserID()
                    val kidParents = kidsModel.parents.orEmpty()
                    val isParentFound = kidParents.contains(loggedInUserId)
                    if (isParentFound) {
                        APIConstants.A = false

                        findNavController().navigate(
                            MyFragmentDirections.actionKidsProfileFragment(
                                kidId = kidsModel._id.orEmpty()
                            )
                        )
                    }
                },
                onCommentClick = { position: Int, type: String, commentId: String, userRelationVal: String, item: MomentListingModel ->
                    when (type) {
                        AppConstants.COMMENT_REPORT -> {
                            findNavController().navigate(
                                MyFragmentDirections.actionReportFragment(
                                    type = ReportType.COMMENT.name,
                                    commentId = commentId
                                )
                            )
                        }
                        AppConstants.COMMENT_DELETE -> {
                            momentViewModel.deleteComment(
                                position = position,
                                commentId = commentId
                            )
                        }
                        AppConstants.USER_PROFILE -> {
                            if (userRelationVal == "SELF") {
                                AppConstants.IS_SHOW_BACK = true
                                findNavController().navigate(MyFragmentDirections.actionProfileFragment(isBackArrowEnabled = true))
                            } else if (userRelationVal == "OTHER_PARENT") {
                                findNavController().navigate(MyFragmentDirections.actionOtherUserProfileFragment(userID = commentId, isOtherParent = true))
                            } else {
                                findNavController().navigate(MyFragmentDirections.actionOtherUserProfileFragment(userID = commentId, isOtherParent = false))
                            }
                        }
                    }

                },
                onMediaClick = { selectedIndex: Int, type: String, url: String ->
                    if (type == MediaType.IMAGE.name || type == MediaType.OG_IMAGE.name) {
                        try {
                            this.findNavController().navigate(
                                MyFragmentDirections.actionUserImageDialog(
                                    url,
                                    isImage = true
                                )
                            )
                        } catch (e: Exception) {
                            this.findNavController().navigate(
                                HomeFragmentDirections.actionUserImageDialog(
                                    url,
                                    isImage = true
                                )
                            )

                        }
                    } else if (type == MediaType.VIDEO.name) {
                        try {
                            this.findNavController().navigate(
                                MyFragmentDirections.actionUserImageDialog(
                                    url,
                                    isLocal = false,
                                    isImage = false,
                                    selectedIndex = selectedIndex
                                )
                            )
                        } catch (e: Exception) {
                            this.findNavController().navigate(
                                HomeFragmentDirections.actionUserImageDialog(
                                    url,
                                    isLocal = false,
                                    isImage = false,
                                    selectedIndex = selectedIndex
                                )
                            )

                        }

//                    this.findNavController().navigate(MyFragmentDirections.actionVideoDialog(url = url))
//                    this.findNavController().navigate(MyFragmentDirections.actionVideoPlayerFragment(isLocal = false, url = url))
                    }
                    else if (type == MediaType.SLIDER.name){

                        try {
                            this.findNavController().navigate(
                                MyFragmentDirections.actionSliderDialog(
                                    url, selectedIndex
                                )
                            )
                        } catch (e: Exception) {
                            try {
                                this.findNavController().navigate(
                                    HomeFragmentDirections.actionSliderDialog(
                                        url, selectedIndex
                                    )
                                )
                            } catch (e: Exception) {
                            }

                        }
                    }

                })
        (binding?.momentList?.itemAnimator as SimpleItemAnimator?)?.supportsChangeAnimations = false
        binding?.momentList?.itemAnimator?.changeDuration = 0
        binding?.momentList?.itemAnimator = null

        binding!!.momentList.adapter = momentAdapter
//        binding.momentList.adapter!!.setHasStableIds(true)


    }
    var momentListJob: Job? = null

    private fun callBookMarkAPI(position: Int, momentId: String) {
        //binding.progressIndicator.showProgressbar()
        momentViewModel.bookMarkMoment(position = position, momentId = momentId)
    }

    private fun callImportatMomentAPI(position: Int, momentId: String) {
        //binding.progressIndicator.showProgressbar()
        momentViewModel.markMomentAsImportant(position = position, momentId = momentId)
    }

    private fun setObserver() {
        homeViewModel.blockedUserObserver.observe(viewLifecycleOwner) {
            binding!!.progressIndicator.hideProgressbar()
            when (it.statusCode) {
                APIConstants.API_RESPONSE_200 -> {
                    var perPage = currentPage * APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
                    currentPage = 1
                    momentList.clear()
                    getMomentList(true, perPage)
                    currentPage = perPage / APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
                }
                APIConstants.UNAUTHORIZED_CODE -> {
                    activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                }
                else -> {
                    DialogUtils.showErrorDialog(
                        requireActivity(),
                        buttonText = resourceProvider.getString(R.string.ok),
                        message = it.message,
                        title = resourceProvider.getString(R.string.app_name)
                    )
                }
            }
        }
        homeViewModel.kidsListObserver.observe(viewLifecycleOwner) {
            it.getContentIfNotHandled()?.let { response ->
                binding!!.kidsShimmerContainer.visibility = View.GONE
                binding!!.kidsList.visibility = VISIBLE
                when (response) {
                    is KidsStatusInfo.UserList -> {
                        kidsAdapter.submitList(response.userList)
                    }
                    else -> {
                        activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                    }
                }
            }
        }
        momentViewModel.momentObserver.observe(viewLifecycleOwner) {
            binding!!.progressIndicator.hideProgressbar()
            it.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is MomentResponseInfo.MomentList -> {
                        if (currentPage == 1) momentList.clear()



                        isLoading = response.momentList.size < APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
                        momentList.addAll(response.momentList)
                        setTotalMomentCount(count = response.count.toIntOrZero())

                        Log.e("DataLoadingfromIR", "IRData-Success")

                        loadMomentList()

                    }
                    is MomentResponseInfo.ApiError -> {
                        activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                    }
                    else -> {
                        isLoading = true
                        loadMomentList()
                    }
                }
            }
        }
        momentViewModel.bookMarkObserver.observe(viewLifecycleOwner) {
            binding!!.progressIndicator.hideProgressbar()
            it.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is BookMarkResponseInfo.BookMarkSuccess -> {
                        response.bookMark?.let { it1 ->
                            momentAdapter.updateMomentDetail(
                                position = response.position,
                                model = it1
                            )
                        }
                    }
                    is BookMarkResponseInfo.ApiError -> {
                        activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                    }
                    is BookMarkResponseInfo.BookMarkFailure -> {
                        DialogUtils.showErrorDialog(
                            requireActivity(),
                            buttonText = resourceProvider.getString(R.string.ok),
                            message = response.message,
                            title = resourceProvider.getString(R.string.app_name)
                        )
                    }
                }
            }
        }
        momentViewModel.markAsImportantMomentObserver.observe(viewLifecycleOwner) {
            binding!!.progressIndicator.hideProgressbar()
            it.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is BookMarkResponseInfo.BookMarkSuccess -> {
                        response.bookMark?.let { it1 ->
                            momentAdapter.updateMomentDetail(
                                position = response.position,
                                model = it1
                            )
                        }
                    }
                    is BookMarkResponseInfo.ApiError -> {
                        activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                    }
                    is BookMarkResponseInfo.BookMarkFailure -> {
                        DialogUtils.showErrorDialog(
                            requireActivity(),
                            buttonText = resourceProvider.getString(R.string.ok),
                            message = response.message,
                            title = resourceProvider.getString(R.string.app_name)
                        )
                    }
                }
            }
        }
        momentViewModel.reactionObserver.observe(viewLifecycleOwner) {
            binding!!.progressIndicator.hideProgressbar()
            it.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is BookMarkResponseInfo.BookMarkSuccess -> {
                        response.bookMark?.let { it1 ->
                            momentAdapter.updateMomentDetail(
                                position = response.position,
                                model = it1
                            )
                        }
                    }
                    is BookMarkResponseInfo.ApiError -> {
                        activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                    }
                    is BookMarkResponseInfo.BookMarkFailure -> {
                        DialogUtils.showErrorDialog(
                            requireActivity(),
                            buttonText = resourceProvider.getString(R.string.ok),
                            message = response.message,
                            title = resourceProvider.getString(R.string.app_name)
                        )
                    }
                }
            }
        }
        momentViewModel.deleteCommentObserver.observe(viewLifecycleOwner) {
            when (it) {
                is CommentDeletedResponseInfo.CommentDelete -> {
                    var perPage = currentPage * APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
                    currentPage = 1
                    momentList.clear()
                    getMomentList(false, perPage)
                    currentPage = perPage / APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
                }
                is CommentDeletedResponseInfo.ApiError -> {
                    binding!!.progressIndicator.hideProgressbar()
                    activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                }
                is CommentDeletedResponseInfo.HaveError -> {
                    binding!!.progressIndicator.hideProgressbar()
                    DialogUtils.showErrorDialog(
                        requireActivity(),
                        buttonText = resourceProvider.getString(R.string.ok),
                        message = it.message,
                        title = resourceProvider.getString(R.string.app_name)
                    )
                }
            }
        }

        momentViewModel.deleteMomentObserver.observe(viewLifecycleOwner) {
            binding!!.progressIndicator.hideProgressbar()
            when (it) {
                is MomentDeletedResponseInfo.MomentDelete -> {
                    var perPage = currentPage * APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
                    currentPage = 1
                    momentList.clear()
                    getMomentList(false, perPage)
                    currentPage = perPage / APIConstants.QUERY_PARAMS_PER_PAGE_VALUE
                }
                is MomentDeletedResponseInfo.ApiError -> {
                    activity?.let { it1 -> DialogUtils.sessionExpiredDialog(it1) }
                }
                is MomentDeletedResponseInfo.HaveError -> {
                    DialogUtils.showErrorDialog(
                        requireActivity(),
                        buttonText = resourceProvider.getString(R.string.ok),
                        message = it.message,
                        title = resourceProvider.getString(R.string.app_name)
                    )
                }
            }
        }

        homeSharedViewModel.homeTabClickedObserver.observe(viewLifecycleOwner) {
            if (viewLifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {

                setMomentListScrollBehaviour()
            }
        }
    }

    private fun setTotalMomentCount(count: Int?) {
        momentAdapter.setTotalMomentCount(count)
    }

    private fun loadMomentList() {
        val currentThread = Thread.currentThread()


        binding!!.momentsShimmerContainer.visibility = View.GONE
        binding!!.progressLoading.visibility = VISIBLE

        if (momentList.isNotEmpty()) {
            binding!!.emptyViewContainer.container.visibility = View.GONE
            binding!!.momentList.visibility = VISIBLE
            momentAdapter.clearGlideMemoryCache(requireContext())
            momentAdapter.submitList(momentList.toList())



//            (binding.momentList?.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        } else {
            binding!!.progressLoading.visibility = View.GONE
            binding!!.momentList.visibility = View.GONE
            binding!!.emptyViewContainer.container.visibility = VISIBLE
            binding!!.noMoreDataText.visibility = GONE
        }
    }

    private fun getKidsList() {
        binding!!.kidsShimmerContainer.visibility = VISIBLE
        binding!!.kidsList.visibility = GONE
        homeViewModel.getKidsList()
    }

    private fun getMomentList(isRequiredShimmer: Boolean, perPage: Int = APIConstants.QUERY_PARAMS_PER_PAGE_VALUE) {
        if (isRequiredShimmer) {
            binding!!.momentList.visibility = GONE
            binding!!.momentsShimmerContainer.visibility = VISIBLE
        }
        isLoading = true
        momentViewModel.getMomentList(
            currentPage,
            MomentType.ALL.name,
            sortBy = MomentDateType.CREATED_DATE.name,
            perPage= perPage
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
//        _binding = null
    }
}