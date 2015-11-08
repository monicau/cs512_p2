package server;

public class ItemHistory {
	public enum ItemType { CUSTOMER, FLIGHT, CAR, ROOM };
	public enum Action { ADDED, RESERVED, DELETED };
	
	private ItemType itemType;
	private Action action;
	private RMItem item;
	private String reservedItemKey;// This would be the flight/car/room's key that was reserved
	
	public ItemHistory(ItemType type, Action a, RMItem item, String itemKey) {
		this.itemType = type;
		this.action = a;
		this.item = item;
		this.reservedItemKey = itemKey;
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
	
	public String getReservedItemKey() {
		return this.reservedItemKey;
	}
}
