package com.tapmedia.yoush.conversationlist.action;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import com.tapmedia.yoush.conversationlist.model.MessageResult;
import com.tapmedia.yoush.recipients.Recipient;

/**
 * -------------------------------------------------------------------------------------------------
 *
 * @Project: Yoush 
 * @Created: Huy 2021/07/18
 * @Organize: Wee Digital
 * @Description: ...
 * All Right Reserved
 * -------------------------------------------------------------------------------------------------
 */
class ActionTemp {

    private void handleDeleteAllSelected() {
        /*int conversationsCount = defaultAdapter.getBatchSelectionIds().size();
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setIconAttribute(R.attr.dialog_alert_icon);
        alert.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                conversationsCount, conversationsCount));
        alert.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                conversationsCount, conversationsCount));
        alert.setCancelable(true);
        alert.setPositiveButton(R.string.delete, (dialog, which) -> {
            final Set<Long> selectedConversations = defaultAdapter.getBatchSelectionIds();

            if (!selectedConversations.isEmpty()) {
                new AsyncTask<Void, Void, Void>() {
                    private ProgressDialog dialog;

                    @Override
                    protected void onPreExecute() {
                        dialog = ProgressDialog.show(getActivity(),
                                getActivity().getString(R.string.ConversationListFragment_deleting),
                                getActivity().getString(R.string.ConversationListFragment_deleting_selected_conversations),
                                true, false);
                    }

                    @Override
                    protected Void doInBackground(Void... params) {
                        ActionJob.deleteConversation(selectedConversations);
                        ApplicationDependencies.getMessageNotifier().updateNotification(getActivity());
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        dialog.dismiss();
                        finishAction();
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();*/
    }

    private void handleSelectAllThreads() {
      /*  defaultAdapter.selectAllThreads();
        actionMode.setTitle(String.valueOf(defaultAdapter.getBatchSelectionIds().size()));*/
    }

    private void handleMarkAllRead() {
        /*Context context = requireContext();
        SignalExecutors.BOUNDED.execute(() -> {
            List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setAllThreadsRead();

            ApplicationDependencies.getMessageNotifier().updateNotification(context);
            MarkReadReceiver.process(context, messageIds);
        });*/
    }

    private void handleMarkSelectedAsRead() {
        /*Context context = requireContext();
        Set<Long> selectedConversations = new HashSet<>(defaultAdapter.getBatchSelectionIds());

        SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(selectedConversations, false);

            ApplicationDependencies.getMessageNotifier().updateNotification(context);
            MarkReadReceiver.process(context, messageIds);

            return null;
        }, none -> {
            finishAction();
        });*/
    }

    private void handleMarkSelectedAsUnread() {
        /*Context context = requireContext();
        Set<Long> selectedConversations = new HashSet<>(defaultAdapter.getBatchSelectionIds());

        SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            DatabaseFactory.getThreadDatabase(context).setForcedUnread(selectedConversations);
            StorageSyncHelper.scheduleSyncForDataChange();
            return null;
        }, none -> {
            finishAction();
        });*/
    }

    private void handleInvite() {
        //getNavigator().goToInvite();
    }

    private void handleInsights() {
        //getNavigator().goToInsights();
    }

    private void handleClearPassphrase() {
        /*Intent intent = new Intent(requireActivity(), KeyCachingService.class);
        intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
        requireActivity().startService(intent);*/
    }

    public void onContactClicked(@NonNull @NotNull Recipient contact) {
        /*cancelSearchIfOpen();
        SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            return DatabaseFactory.getThreadDatabase(getContext()).getThreadIdIfExistsFor(contact);
        }, threadId -> {
            hideKeyboard();
            getNavigator().goToConversation(contact.getId(),
                    threadId,
                    ThreadDatabase.DistributionTypes.DEFAULT,
                    -1);
        });*/
    }

    public void onMessageClicked(@NonNull @NotNull MessageResult message) {
       /* cancelSearchIfOpen();
        SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            int startingPosition = DatabaseFactory.getMmsSmsDatabase(getContext()).getMessagePositionInConversation(message.threadId, message.receivedTimestampMs);
            return Math.max(0, startingPosition);
        }, startingPosition -> {
            hideKeyboard();
            getNavigator().goToConversation
                    (message.conversationRecipient.getId(),
                            message.threadId,
                            ThreadDatabase.DistributionTypes.DEFAULT,
                            startingPosition);
        });*/
    }

}
