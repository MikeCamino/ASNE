package com.androidsocialnetworks.lib.impl;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.androidsocialnetworks.lib.AccessToken;
import com.androidsocialnetworks.lib.SocialNetwork;
import com.androidsocialnetworks.lib.SocialNetworkException;
import com.androidsocialnetworks.lib.listener.OnCheckIsFriendCompleteListener;
import com.androidsocialnetworks.lib.listener.OnLoginCompleteListener;
import com.androidsocialnetworks.lib.listener.OnPostingCompleteListener;
import com.androidsocialnetworks.lib.listener.OnRequestAccessTokenCompleteListener;
import com.androidsocialnetworks.lib.listener.OnRequestAddFriendCompleteListener;
import com.androidsocialnetworks.lib.listener.OnRequestDetailedSocialPersonCompleteListener;
import com.androidsocialnetworks.lib.listener.OnRequestGetFriendsCompleteListener;
import com.androidsocialnetworks.lib.listener.OnRequestRemoveFriendCompleteListener;
import com.androidsocialnetworks.lib.listener.OnRequestSocialPersonCompleteListener;
import com.androidsocialnetworks.lib.listener.OnRequestSocialPersonsCompleteListener;
import com.androidsocialnetworks.lib.persons.FacebookPerson;
import com.androidsocialnetworks.lib.persons.SocialPerson;
import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionDefaultAudience;
import com.facebook.SessionLoginBehavior;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.internal.SessionTracker;
import com.facebook.internal.Utility;
import com.facebook.model.GraphUser;
import com.facebook.widget.FacebookDialog;
import com.facebook.widget.WebDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FacebookSocialNetwork extends SocialNetwork {
    public static final int ID = 4;

    private static final String TAG = FacebookSocialNetwork.class.getSimpleName();
    private static final String PERMISSION = "publish_actions";
    private SessionTracker mSessionTracker;
    private UiLifecycleHelper mUILifecycleHelper;
    private String mApplicationId;
    private SessionState mSessionState;
    private String mPhotoPath;
    private String mStatus;
    private Bundle mBundle;
    private ArrayList<String> permissions;
    private PendingAction mPendingAction = PendingAction.NONE;
    private Session.StatusCallback mSessionStatusCallback = new Session.StatusCallback() {
        @Override
        public void call(Session session, SessionState state, Exception exception) {
            onSessionStateChange(session, state, exception);
        }
    };

    public FacebookSocialNetwork(Fragment fragment, ArrayList<String> permissions) {
        super(fragment);
        this.permissions = permissions;
    }

    @Override
    public boolean isConnected() {
        Session session = Session.getActiveSession();
        return (session != null && session.isOpened());
    }

    @Override
    public void requestLogin(OnLoginCompleteListener onLoginCompleteListener) {
        super.requestLogin(onLoginCompleteListener);

        final Session openSession = mSessionTracker.getOpenSession();

        if (openSession != null) {
            if (mLocalListeners.get(REQUEST_LOGIN) != null) {
                mLocalListeners.get(REQUEST_LOGIN).onError(getID(), REQUEST_LOGIN, "Already loginned", null);
            }
        }

        Session currentSession = mSessionTracker.getSession();
        if (currentSession == null || currentSession.getState().isClosed()) {
            mSessionTracker.setSession(null);
            Session session = new Session.Builder(mSocialNetworkManager.getActivity())
                    .setApplicationId(mApplicationId).build();
            Session.setActiveSession(session);
            currentSession = session;
        }

        if (!currentSession.isOpened()) {
            Session.OpenRequest openRequest;
            openRequest = new Session.OpenRequest(mSocialNetworkManager);

            openRequest.setDefaultAudience(SessionDefaultAudience.EVERYONE);
            if(permissions != null) {
                openRequest.setPermissions(permissions);//Collections.<String>emptyList());//
            }
            openRequest.setLoginBehavior(SessionLoginBehavior.SSO_WITH_FALLBACK);

            currentSession.openForRead(openRequest);
        }
    }

    @Override
    public void requestAccessToken(OnRequestAccessTokenCompleteListener onRequestAccessTokenCompleteListener) {
        super.requestAccessToken(onRequestAccessTokenCompleteListener);
        ((OnRequestAccessTokenCompleteListener) mLocalListeners.get(REQUEST_ACCESS_TOKEN))
                .onRequestAccessTokenComplete(getID(), new AccessToken(Session.getActiveSession().getAccessToken(), null));
    }

    @Override
    public void logout() {
        if (mSessionTracker == null) return;

        final Session openSession = mSessionTracker.getOpenSession();

        if (openSession != null) {
            openSession.closeAndClearTokenInformation();
        }
    }

    @Override
    public int getID() {
        return ID;
    }

	@Override
    public AccessToken getAccessToken() {
        return new AccessToken(Session.getActiveSession().getAccessToken(), null);
    }

    @Override
    public void requestCurrentPerson(OnRequestSocialPersonCompleteListener onRequestSocialPersonCompleteListener) {
        super.requestCurrentPerson(onRequestSocialPersonCompleteListener);

        final Session currentSession = mSessionTracker.getOpenSession();

        if (currentSession == null) {
            if (mLocalListeners.get(REQUEST_GET_CURRENT_PERSON) != null) {
                mLocalListeners.get(REQUEST_GET_CURRENT_PERSON).onError(getID(),
                        REQUEST_GET_PERSON, "Please login first", null);
            }
            return;
        }

        Request request = Request.newMeRequest(currentSession, new Request.GraphUserCallback() {
            @Override
            public void onCompleted(GraphUser me, Response response) {
                if (response.getError() != null) {
                    if (mLocalListeners.get(REQUEST_GET_CURRENT_PERSON) != null) {
                        mLocalListeners.get(REQUEST_GET_CURRENT_PERSON).onError(
                                getID(), REQUEST_GET_CURRENT_PERSON, response.getError().getErrorMessage()
                                , null);
                    }
                    return;
                }
                if (mLocalListeners.get(REQUEST_GET_CURRENT_PERSON) != null) {
                    SocialPerson socialPerson = new SocialPerson();
                    getSocialPerson(socialPerson, me);
                    ((OnRequestSocialPersonCompleteListener) mLocalListeners.get(REQUEST_GET_CURRENT_PERSON))
                            .onRequestSocialPersonSuccess(getID(), socialPerson);
                }
            }
        });
        request.executeAsync();
    }

    @Override
    public void requestSocialPerson(String userID, OnRequestSocialPersonCompleteListener onRequestSocialPersonCompleteListener) {
        throw new SocialNetworkException("requestSocialPerson isn't allowed for FacebookSocialNetwork");
    }

	@Override
    public void requestSocialPersons(String[] userID, OnRequestSocialPersonsCompleteListener onRequestSocialPersonsCompleteListener) {
        throw new SocialNetworkException("requestSocialPersons isn't allowed for FacebookSocialNetwork");
    }

    @Override
    public void requestDetailedSocialPerson(String userId, OnRequestDetailedSocialPersonCompleteListener onRequestDetailedSocialPersonCompleteListener) {
        super.requestDetailedSocialPerson(userId, onRequestDetailedSocialPersonCompleteListener);
        final Session currentSession = mSessionTracker.getOpenSession();

        if (currentSession == null) {
            if (mLocalListeners.get(REQUEST_GET_DETAIL_PERSON) != null) {
                mLocalListeners.get(REQUEST_GET_DETAIL_PERSON).onError(getID(),
                        REQUEST_GET_DETAIL_PERSON, "Please login first", null);
            }

            return;
        }

        Request request = Request.newMeRequest(currentSession, new Request.GraphUserCallback() {
            @Override
            public void onCompleted(GraphUser me, Response response) {
                if (response.getError() != null) {
                    if (mLocalListeners.get(REQUEST_GET_DETAIL_PERSON) != null) {
                        mLocalListeners.get(REQUEST_GET_DETAIL_PERSON).onError(
                                getID(), REQUEST_GET_DETAIL_PERSON, response.getError().getErrorMessage()
                                , null);
                    }
                    return;
                }

                if (mLocalListeners.get(REQUEST_GET_DETAIL_PERSON) != null) {
                    FacebookPerson facebookPerson = new FacebookPerson();
                    getDetailedSocialPerson(facebookPerson, me);

                    ((OnRequestDetailedSocialPersonCompleteListener) mLocalListeners.get(REQUEST_GET_DETAIL_PERSON))
                            .onRequestDetailedSocialPersonSuccess(getID(), facebookPerson);
                }
            }
        });
        request.executeAsync();
    }

    private SocialPerson getSocialPerson(SocialPerson socialPerson, GraphUser user){
        socialPerson.id = user.getId();
        socialPerson.name = user.getName();
        socialPerson.avatarURL = String.format("https://graph.facebook.com/%s/picture?type=large", user.getId());
        if(user.getLink() != null) {
            socialPerson.profileURL = user.getLink();
        } else {
            socialPerson.profileURL = String.format("https://www.facebook.com/", user.getId());
        }
        if(user.getProperty("email") != null){
            socialPerson.email = user.getProperty("email").toString();
        }
        return socialPerson;
    }

	private FacebookPerson getDetailedSocialPerson(FacebookPerson facebookPerson, GraphUser user){
        getSocialPerson(facebookPerson, user);
        facebookPerson.firstName = user.getFirstName();
        facebookPerson.middleName = user.getMiddleName();
        facebookPerson.lastName = user.getLastName();
        if(user.getProperty("gender") != null) {
            facebookPerson.gender = user.getProperty("gender").toString();
        }
        facebookPerson.birthday = user.getBirthday();
        if(user.getLocation() != null) {
            facebookPerson.city = user.getLocation().getProperty("name").toString();
        }
        facebookPerson.verified = user.getProperty("verified").toString();
        return facebookPerson;
    }

    @Override
    public void requestPostMessage(String message, OnPostingCompleteListener onPostingCompleteListener) {
        super.requestPostMessage(message, onPostingCompleteListener);
        mStatus = message;
        performPublish(PendingAction.POST_STATUS_UPDATE);
    }

    @Override
    public void requestPostPhoto(File photo, String message, OnPostingCompleteListener onPostingCompleteListener) {
        super.requestPostPhoto(photo, message, onPostingCompleteListener);
        mPhotoPath = photo.getAbsolutePath();
        performPublish(PendingAction.POST_PHOTO);
    }

    @Override
    public void requestPostLink(Bundle bundle, String message, OnPostingCompleteListener onPostingCompleteListener) {
        super.requestPostLink(bundle, message, onPostingCompleteListener);
        mBundle = bundle;
        performPublish(PendingAction.POST_LINK);
    }

    @Override
    public void requestPostDialog(Bundle bundle, OnPostingCompleteListener onPostingCompleteListener) {
        super.requestPostDialog(bundle, onPostingCompleteListener);
        if (FacebookDialog.canPresentShareDialog(mSocialNetworkManager.getActivity(),
                FacebookDialog.ShareDialogFeature.SHARE_DIALOG)) {
            FacebookDialog shareDialog = new FacebookDialog.ShareDialogBuilder(mSocialNetworkManager.getActivity())
                    .setLink(bundle.getString(BUNDLE_LINK))
                    .setDescription(bundle.getString(BUNDLE_MESSAGE))
                    .setName(bundle.getString(BUNDLE_NAME))
                    .setApplicationName(bundle.getString(BUNDLE_APP_NAME))
                    .setCaption(bundle.getString(BUNDLE_CAPTION))
                    .setPicture(bundle.getString(BUNDLE_PICTURE))
//                    .setFriends(bundle.getStringArrayList(DIALOG_FRIENDS))
                    .build();
            mUILifecycleHelper.trackPendingDialogCall(shareDialog.present());
        } else {
            publishFeedDialog(bundle);
        }
    }

    private void publishFeedDialog(Bundle bundle) {
        Bundle params = new Bundle();
        params.putString("name", bundle.getString(BUNDLE_NAME));
        params.putString("caption", bundle.getString(BUNDLE_CAPTION));
        params.putString("description", bundle.getString(BUNDLE_MESSAGE));
        params.putString("link", bundle.getString(BUNDLE_LINK));
        params.putString("picture", bundle.getString(BUNDLE_PICTURE));

        WebDialog feedDialog = (
                new WebDialog.FeedDialogBuilder(mSocialNetworkManager.getActivity(),
                        Session.getActiveSession(),
                        params))
                .setOnCompleteListener(new WebDialog.OnCompleteListener() {
                    @Override
                    public void onComplete(Bundle values,
                                           FacebookException error) {
                        if (error == null) {
                            final String postId = values.getString("post_id");
                            if (postId != null) {
                                ((OnPostingCompleteListener) mLocalListeners.get(REQUEST_POST_DIALOG)).onPostSuccessfully(getID());
                            } else {
                                mLocalListeners.get(REQUEST_POST_DIALOG).onError(getID(),
                                        REQUEST_POST_DIALOG, "Canceled", null);
                            }
                        } else {
                            mLocalListeners.get(REQUEST_POST_DIALOG).onError(getID(),
                                    REQUEST_POST_DIALOG, "Canceled: " + error.toString(), null);
                        }
                        mLocalListeners.remove(REQUEST_POST_DIALOG);
                    }
                })
                .build();
        feedDialog.show();
    }

    private void performPublish(PendingAction action) {
        Session session = Session.getActiveSession();
        if (session != null) {
            mPendingAction = action;
            if (session.isPermissionGranted(PERMISSION)) {
                // We can do the action right away.
                handlePendingAction();
                return;
            } else if (session.isOpened()) {
                // We need to get new permissions, then complete the action when we get called back.
                session.requestNewPublishPermissions(new Session.NewPermissionsRequest(mSocialNetworkManager.getActivity(), PERMISSION));
                return;
            }
        }

        if (action == PendingAction.POST_STATUS_UPDATE) {
            if (mLocalListeners.get(REQUEST_POST_MESSAGE) != null) {
                mLocalListeners.get(REQUEST_POST_MESSAGE).onError(getID(),
                        REQUEST_POST_MESSAGE, "no session", null);
            }
        }

        if (action == PendingAction.POST_PHOTO) {
            if (mLocalListeners.get(REQUEST_POST_PHOTO) != null) {
                mLocalListeners.get(REQUEST_POST_PHOTO).onError(getID(),
                        REQUEST_POST_PHOTO, "no session", null);
            }
        }

        if (action == PendingAction.POST_LINK) {
            if (mLocalListeners.get(REQUEST_POST_LINK) != null) {
                mLocalListeners.get(REQUEST_POST_LINK).onError(getID(),
                        REQUEST_POST_LINK, "no session", null);
            }
        }
    }

    @Override
    public void requestCheckIsFriend(String userID, OnCheckIsFriendCompleteListener onCheckIsFriendCompleteListener) {
        throw new SocialNetworkException("requestCheckIsFriend isn't allowed for FacebookSocialNetwork");
    }
	
	@Override
    public void requestGetFriends(OnRequestGetFriendsCompleteListener onRequestGetFriendsCompleteListener) {
        super.requestGetFriends(onRequestGetFriendsCompleteListener);
		final Session currentSession = mSessionTracker.getOpenSession();

        if (currentSession == null) {
            if (mLocalListeners.get(REQUEST_GET_FRIENDS) != null) {
                mLocalListeners.get(REQUEST_GET_FRIENDS).onError(getID(),
                        REQUEST_GET_FRIENDS, "Please login first", null);
            }

            return;
        }

        Request request = Request.newMyFriendsRequest(currentSession, new Request.GraphUserListCallback() {
            @Override
            public void onCompleted(List<GraphUser> users, Response response) {
                String[] ids = new String[users.size()];
                ArrayList<SocialPerson> socialPersons = new ArrayList<SocialPerson>();
                SocialPerson socialPerson = new SocialPerson();
                int i = 0;
                for(GraphUser user : users) {
                    getSocialPerson(socialPerson, user);
                    socialPersons.add(socialPerson);
                    socialPerson = new SocialPerson();
                    ids[i] = user.getId();
                    i++;
                }
                ((OnRequestGetFriendsCompleteListener) mLocalListeners.get(REQUEST_GET_FRIENDS))
                        .OnGetFriendsIdComplete(getID(), ids);
                ((OnRequestGetFriendsCompleteListener) mLocalListeners.get(REQUEST_GET_FRIENDS))
                        .OnGetFriendsComplete(getID(), socialPersons);
                mLocalListeners.remove(REQUEST_GET_FRIENDS);
            }
        });
        request.executeAsync();
    }

    @Override
    public void requestAddFriend(String userID, OnRequestAddFriendCompleteListener onRequestAddFriendCompleteListener) {
        throw new SocialNetworkException("requestAddFriend isn't allowed for FacebookSocialNetwork");
    }

    @Override
    public void requestRemoveFriend(String userID, OnRequestRemoveFriendCompleteListener onRequestRemoveFriendCompleteListener) {
        throw new SocialNetworkException("requestRemoveFriend isn't allowed for FacebookSocialNetwork");
    }

    private void onSessionStateChange(Session session, SessionState state, Exception exception) {
        Log.d(TAG, "onSessionStateChange: " + state + " : " + exception + " WAT: " + session.getPermissions());

        if (mSessionState == SessionState.OPENING && state == SessionState.OPENED) {
            if (mLocalListeners.get(REQUEST_LOGIN) != null) {
                ((OnLoginCompleteListener) mLocalListeners.get(REQUEST_LOGIN)).onLoginSuccess(getID());
                mLocalListeners.remove(REQUEST_LOGIN);
            }
        }

        if (state == SessionState.CLOSED_LOGIN_FAILED) {
            if (mLocalListeners.get(REQUEST_LOGIN) != null) {
                mLocalListeners.get(REQUEST_LOGIN).onError(getID(), REQUEST_LOGIN, exception.getMessage(), null);
                mLocalListeners.remove(REQUEST_LOGIN);
            }
        }

        mSessionState = state;

        if (mPendingAction != PendingAction.NONE &&
                (exception instanceof FacebookOperationCanceledException ||
                        exception instanceof FacebookAuthorizationException)) {
            mPendingAction = PendingAction.NONE;

            if (mLocalListeners.get(REQUEST_POST_MESSAGE) != null) {
                mLocalListeners.get(REQUEST_POST_MESSAGE).onError(getID(),
                        REQUEST_POST_MESSAGE, "permission not granted", null);
            }

            if (mLocalListeners.get(REQUEST_POST_PHOTO) != null) {
                mLocalListeners.get(REQUEST_POST_PHOTO).onError(getID(),
                        REQUEST_POST_PHOTO, "permission not granted", null);
            }

            if (mLocalListeners.get(REQUEST_POST_LINK) != null) {
                mLocalListeners.get(REQUEST_POST_LINK).onError(getID(),
                        REQUEST_POST_LINK, "permission not granted", null);
            }
        }

        if (session.isPermissionGranted(PERMISSION)
                && state == SessionState.OPENED_TOKEN_UPDATED) {
            handlePendingAction();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUILifecycleHelper = new UiLifecycleHelper(mSocialNetworkManager.getActivity(), mSessionStatusCallback);
        mUILifecycleHelper.onCreate(savedInstanceState);

        initializeActiveSessionWithCachedToken(mSocialNetworkManager.getActivity());
        finishInit();
    }

    private boolean initializeActiveSessionWithCachedToken(Context context) {
        if (context == null) {
            return false;
        }

        Session session = Session.getActiveSession();
        if (session != null) {
            return session.isOpened();
        }

        mApplicationId = Utility.getMetadataApplicationId(context);
        return mApplicationId != null && Session.openActiveSessionFromCache(context) != null;

    }

    private void finishInit() {
        mSessionTracker = new SessionTracker(
                mSocialNetworkManager.getActivity(), mSessionStatusCallback, null, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        mUILifecycleHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mUILifecycleHelper.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUILifecycleHelper.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mUILifecycleHelper.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mUILifecycleHelper.onActivityResult(requestCode, resultCode, data, null);

        Session session = mSessionTracker.getOpenSession();
        if (session != null) {
            session.onActivityResult(mSocialNetworkManager.getActivity(), requestCode, resultCode, data);
        }

        mUILifecycleHelper.onActivityResult(requestCode, resultCode, data, new FacebookDialog.Callback() {
            @Override
            public void onError(FacebookDialog.PendingCall pendingCall, Exception error, Bundle data) {
                Log.e("Activity", String.format("Error: %s", error.toString()));
            }

            @Override
            public void onComplete(FacebookDialog.PendingCall pendingCall, Bundle data) {
            }
        });
    }

    private void handlePendingAction() {
        PendingAction previouslyPendingAction = mPendingAction;
        // These actions may re-set pendingAction if they are still pending, but we assume they
        // will succeed.
        mPendingAction = PendingAction.NONE;

        switch (previouslyPendingAction) {
            case POST_PHOTO:
                postPhoto(mPhotoPath);
                break;
            case POST_STATUS_UPDATE:
                postStatusUpdate(mStatus);
                break;
            case POST_LINK:
                postLink(mBundle);
                break;
        }
    }

    private void postStatusUpdate(String message) {
        if (isConnected() && Session.getActiveSession().isPermissionGranted(PERMISSION)){
            Request request = Request
                    .newStatusUpdateRequest(Session.getActiveSession(), message, null, null, new Request.Callback() {
                        @Override
                        public void onCompleted(Response response) {
                            publishSuccess(REQUEST_POST_MESSAGE,
                                    response.getError() == null ? null : response.getError().getErrorMessage());
                        }
                    });
            request.executeAsync();
        } else {
            mPendingAction = PendingAction.POST_STATUS_UPDATE;
        }
    }

    private void postPhoto(final String path) {
        if (Session.getActiveSession().isPermissionGranted(PERMISSION)){
            Bitmap image = BitmapFactory.decodeFile(path);
            Request request = Request.newUploadPhotoRequest(Session.getActiveSession(), image, new Request.Callback() {
                @Override
                public void onCompleted(Response response) {
                    publishSuccess(REQUEST_POST_PHOTO,
                            response.getError() == null ? null : response.getError().getErrorMessage());
                }
            });
            request.executeAsync();
        } else {
            mPendingAction = PendingAction.POST_PHOTO;
        }
    }

    private void postLink(final Bundle bundle) {
        if (Session.getActiveSession().isPermissionGranted(PERMISSION)){
            Request request = new Request(Session.getActiveSession(), "me/feed", bundle,
                    HttpMethod.POST, new Request.Callback(){
                @Override
                public void onCompleted(Response response) {
                    publishSuccess(REQUEST_POST_LINK,
                            response.getError() == null ? null : response.getError().getErrorMessage());
                }
            });
            request.executeAsync();
        } else {
            mPendingAction = PendingAction.POST_PHOTO;
        }
    }

    private void publishSuccess(String requestID, String error) {
        if (mLocalListeners.get(requestID) == null) return;

        if (error != null) {
            mLocalListeners.get(requestID).onError(getID(), requestID, error, null);
            return;
        }

        ((OnPostingCompleteListener) mLocalListeners.get(requestID)).onPostSuccessfully(getID());
        mLocalListeners.remove(requestID);
    }

    private enum PendingAction {
        NONE,
        POST_PHOTO,
        POST_STATUS_UPDATE,
        POST_LINK
    }
}