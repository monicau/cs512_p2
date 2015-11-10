package server;

public class ItemHistory {
	public enum ItemType { CUSTOMER, FLIGHT, CAR, ROOM };
	public enum Action { ADDED, UPDATED, RESERVED, DELETED };
	
	private ItemType itemType;
	private Action action;
	private RMItem item;
	private String reservedItemKey;// This would be the flight/car/room's key that was reserved
	
	private Integer oldCount;
	private Integer oldPrice;
	private Integer oldReserved;
	private String  oldLocation;
	
	public ItemHistory(ItemType type, Action a, RMItem item, String itemKey) {
		this(type, a, item, itemKey, null, null, null, null);
	}
	
	public ItemHistory(ItemType type, Action a, RMItem item, String itemKey, Integer oldCount, Integer oldPrice, Integer oldReserved, String oldLocation) {
		this.itemType = type;
		this.action = a;
		this.item = item;
		this.reservedItemKey = itemKey;
		
		this.oldCount = oldCount;
		this.oldPrice = oldPrice;
		this.oldReserved = oldReserved;
		this.oldLocation = oldLocation;
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
