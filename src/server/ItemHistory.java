package server;

public class ItemHistory {
	public enum ItemType { CUSTOMER, FLIGHT, CAR, ROOM };
	public enum Action { ADDED, UPDATED, DELETED };
	
	private ItemType itemType;
	private Action action;
	private RMItem item;
	
	public ItemHistory(ItemType type, Action a, RMItem item) {
		this.itemType = type;
		this.action = a;
		this.item = item;
	}
	
	public ItemType getItemType() {
		return this.itemType;
	}
	
	public Action getAction() {
		return this.action;
	}
	
	public RMItem getItem() {
		return this.item;
	}
}
