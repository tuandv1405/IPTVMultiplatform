import Foundation
import GoogleSignIn
import GoogleSignInSwift

// This class serves as a bridge between Kotlin and the GoogleSignIn iOS SDK
@objc public class GoogleSignInBridge: NSObject {
    
    // Singleton instance
    @objc static let shared = GoogleSignInBridge()
    
    private override init() {
        super.init()
    }
    
    // MARK: - Sign In Methods
    
    // Sign in with Google
    @objc public func signIn(clientID: String, completion: @escaping ([String: Any]?, Error?) -> Void) {
        let config = GIDConfiguration(clientID: clientID)
        
        // Get the top view controller to present the sign-in UI
        guard let topViewController = UIApplication.shared.windows.first?.rootViewController else {
            completion(nil, NSError(domain: "GoogleSignInBridge", code: -1, userInfo: [NSLocalizedDescriptionKey: "No view controller available to present sign-in UI"]))
            return
        }
        
        GIDSignIn.sharedInstance.signIn(with: config, presenting: topViewController) { user, error in
            if let error = error {
                completion(nil, error)
                return
            }
            
            guard let user = user else {
                completion(nil, NSError(domain: "GoogleSignInBridge", code: -1, userInfo: [NSLocalizedDescriptionKey: "No user found"]))
                return
            }
            
            // Get the ID token
            user.authentication.do { authentication, error in
                if let error = error {
                    completion(nil, error)
                    return
                }
                
                guard let authentication = authentication else {
                    completion(nil, NSError(domain: "GoogleSignInBridge", code: -1, userInfo: [NSLocalizedDescriptionKey: "No authentication found"]))
                    return
                }
                
                let userData: [String: Any] = [
                    "idToken": authentication.idToken ?? "",
                    "accessToken": authentication.accessToken,
                    "userId": user.userID ?? "",
                    "email": user.profile?.email ?? "",
                    "displayName": user.profile?.name ?? "",
                    "photoUrl": user.profile?.imageURL(withDimension: 100)?.absoluteString ?? ""
                ]
                
                completion(userData, nil)
            }
        }
    }
    
    // Sign out
    @objc public func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }
    
    // Check if user is signed in
    @objc public func isSignedIn() -> Bool {
        return GIDSignIn.sharedInstance.hasPreviousSignIn()
    }
    
    // Get current user
    @objc public func getCurrentUser(completion: @escaping ([String: Any]?, Error?) -> Void) {
        if let currentUser = GIDSignIn.sharedInstance.currentUser {
            currentUser.authentication.do { authentication, error in
                if let error = error {
                    completion(nil, error)
                    return
                }
                
                guard let authentication = authentication else {
                    completion(nil, NSError(domain: "GoogleSignInBridge", code: -1, userInfo: [NSLocalizedDescriptionKey: "No authentication found"]))
                    return
                }
                
                let userData: [String: Any] = [
                    "idToken": authentication.idToken ?? "",
                    "accessToken": authentication.accessToken,
                    "userId": currentUser.userID ?? "",
                    "email": currentUser.profile?.email ?? "",
                    "displayName": currentUser.profile?.name ?? "",
                    "photoUrl": currentUser.profile?.imageURL(withDimension: 100)?.absoluteString ?? ""
                ]
                
                completion(userData, nil)
            }
        } else {
            completion(nil, nil)
        }
    }
    
    // Restore previous sign-in
    @objc public func restorePreviousSignIn(completion: @escaping ([String: Any]?, Error?) -> Void) {
        GIDSignIn.sharedInstance.restorePreviousSignIn { user, error in
            if let error = error {
                completion(nil, error)
                return
            }
            
            guard let user = user else {
                completion(nil, nil)
                return
            }
            
            user.authentication.do { authentication, error in
                if let error = error {
                    completion(nil, error)
                    return
                }
                
                guard let authentication = authentication else {
                    completion(nil, NSError(domain: "GoogleSignInBridge", code: -1, userInfo: [NSLocalizedDescriptionKey: "No authentication found"]))
                    return
                }
                
                let userData: [String: Any] = [
                    "idToken": authentication.idToken ?? "",
                    "accessToken": authentication.accessToken,
                    "userId": user.userID ?? "",
                    "email": user.profile?.email ?? "",
                    "displayName": user.profile?.name ?? "",
                    "photoUrl": user.profile?.imageURL(withDimension: 100)?.absoluteString ?? ""
                ]
                
                completion(userData, nil)
            }
        }
    }
}