import Foundation

// Define a class Player.
// Make the Player class Identifiable for potential use in UI lists.
class Player: Identifiable {
    // Add an id property for Identifiable conformance
    let id = UUID()

    // Add a property name: String to store the player's name.
    var name: String

    // Add a property hand: [Card] initialized as an empty array,
    // to store the cards held by the player.
    var hand: [Card] = []

    // Add an initializer init(name: String) to set the player's name.
    init(name: String) {
        self.name = name
    }
}
