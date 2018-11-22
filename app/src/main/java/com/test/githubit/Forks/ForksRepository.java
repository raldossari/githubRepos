package com.test.githubit.Forks;

import com.test.githubit.http.UserApiService;
import com.test.githubit.http.apimodel.Fork;
import com.test.githubit.http.apimodel.ForkMemory;
import com.test.githubit.http.apimodel.UserDetails;
import com.test.githubit.http.apimodel.UserDetailsMemory;
import java.util.ArrayList;
import java.util.List;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class ForksRepository implements Repository {

    private UserApiService userApiService;
    private List<UserDetails> userDetails;
    private UserDetailsMemory userDetailsMemory;
    private ForkMemory forkMemory;
    private List<Fork> forks;
    private long timestamp;
    private String client_id= "d7b05bbfdcf1e4457fea";
    private String client_secret="ec564681514e3488f2e1917e403a8d240470fcbb";

    private static final long STALE_MS = 20 * 1000; // Data is stale after 20 seconds

    public ForksRepository(UserApiService userApiService) {
        this.timestamp = System.currentTimeMillis();
        this.userApiService = userApiService;
        userDetails = new ArrayList<>();
        forks = new ArrayList<>();
        forkMemory = new ForkMemory();
        userDetailsMemory = new UserDetailsMemory();
    }

    public boolean isUpToDate() {
        return System.currentTimeMillis() - timestamp < STALE_MS;
    }

    @Override
    public Observable<Fork> getUsersFromMemory(String repoName, String username) {
        if (isUpToDate()&& forkMemory.getName().equals(repoName)) {

            return Observable.fromIterable(forks);
        } else {
            timestamp = System.currentTimeMillis();
            forks.clear();
            forkMemory.setForks(forks);
            forkMemory.setName(username);
            return Observable.empty();
        }
    }

    @Override
    public Observable<Fork> getUsersFromNetwork(String repoName, String username) {
        forkMemory.setName(repoName);
        Observable<List<Fork>> forkObservable = userApiService.getUser(username,repoName,client_id,client_secret);
        return forkObservable.concatMap(new Function<List<Fork>, Observable<Fork>>() {
            @Override
            public Observable<Fork> apply(List<Fork> users) throws Exception {
                forkMemory.setForks(users);
                return Observable.fromIterable(users);
            }
        }).doOnNext(new Consumer<Fork>() {
            @Override
            public void accept(Fork result) {
                forks.add(result);
            }
        });

    }

    @Override
    public Observable<UserDetails> getUserDetailsFromMemory(String repoName, String username) {
        if (isUpToDate()&& userDetailsMemory.getName().equals(username)) {

            return Observable.fromIterable(userDetails);
        } else {
            timestamp = System.currentTimeMillis();
            userDetails.clear();
            userDetailsMemory.setUserDetails(userDetails);
            userDetailsMemory.setName(username);
            return Observable.empty();
        }
    }

    @Override
    public Observable<UserDetails> getUserDetailsFromNetwork(String repoName, String username) {
        return getUsersFromNetwork(repoName, username).concatMap(new Function<Fork, Observable<UserDetails>>() {
            @Override
            public Observable<UserDetails> apply(Fork result) {
                return userApiService.getUserDetails(result.getOwner().getLogin(),client_id,client_secret );
            }
        }).concatMap(new Function<UserDetails, Observable<UserDetails>>() {
            @Override
            public Observable<UserDetails> apply(UserDetails userDetails) {
                return Observable.just(userDetails);
            }
        }).doOnNext(new Consumer<UserDetails>() {
            @Override
            public void accept(UserDetails userDetails) throws Exception {
                ForksRepository.this.userDetails.add(userDetails);
            }
        });
    }

    @Override
    public Observable<UserDetails> getUserDetailsData(String repoName, String username) {
        return getUserDetailsFromMemory(repoName, username).switchIfEmpty(getUserDetailsFromNetwork(repoName, username));
    }

    @Override
    public Observable<Fork> getOwnerData(String repoName, String username) {
        return getUsersFromMemory(repoName, username).switchIfEmpty(getUsersFromNetwork(repoName, username));
    }
}