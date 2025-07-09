//
//  GoogleSignInBridge.h
//  GoogleSignInKit
//
//  Created by Tun on 3/7/25.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface GoogleSignInBridge : NSObject

+ (instancetype)shared;

// Sign in with Google
- (void)signInWithClientID:(NSString *)clientID
                completion:(void (^)(NSDictionary * _Nullable userData, NSError * _Nullable error))completion;

// Sign out
- (void)signOut;

// Check if user is signed in
- (BOOL)isSignedIn;

// Get current user
- (void)getCurrentUserWithCompletion:(void (^)(NSDictionary * _Nullable userData, NSError * _Nullable error))completion;

// Restore previous sign-in
- (void)restorePreviousSignInWithCompletion:(void (^)(NSDictionary * _Nullable userData, NSError * _Nullable error))completion;

@end

NS_ASSUME_NONNULL_END