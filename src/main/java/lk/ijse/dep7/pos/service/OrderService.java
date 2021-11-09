package lk.ijse.dep7.pos.service;

import lk.ijse.dep7.pos.dao.OrderDAO;
import lk.ijse.dep7.pos.dto.ItemDTO;
import lk.ijse.dep7.pos.dto.OrderDTO;
import lk.ijse.dep7.pos.dto.OrderDetailDTO;
import lk.ijse.dep7.pos.exception.DuplicateIdentifierException;
import lk.ijse.dep7.pos.exception.FailedOperationException;
import lk.ijse.dep7.pos.exception.NotFoundException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class OrderService {

    private Connection connection;
    private OrderDAO orderDAO;

    public OrderService(Connection connection) {
        this.connection = connection;
        this.orderDAO = new OrderDAO(connection);
    }

    public void saveOrder(String orderId, LocalDate orderDate, String customerId, List<OrderDetailDTO> orderDetails) throws FailedOperationException, DuplicateIdentifierException, NotFoundException {

        final CustomerService customerService = new CustomerService(connection);
        final ItemService itemService = new ItemService(connection);

        try {
            connection.setAutoCommit(false);

            if (orderDAO.existsOrder(orderId)) {
                throw new DuplicateIdentifierException(orderId + " already exists");
            }

            if (!customerService.existCustomer(customerId)) {
                throw new NotFoundException("Customer id doesn't exist");
            }

            orderDAO.saveOrder(orderId, orderDate, customerId);

            for (OrderDetailDTO detail : orderDetails) {
                orderDAO.saveOrderDetail(orderId, detail);

                ItemDTO item = itemService.findItem(detail.getItemCode());
                item.setQtyOnHand(item.getQtyOnHand() - detail.getQty());
                itemService.updateItem(item);
            }

            connection.commit();

        } catch (SQLException e) {
            failedOperationExecutionContext(connection::rollback);
        } catch (Throwable t) {
            failedOperationExecutionContext(connection::rollback);
            throw t;
        } finally {
            failedOperationExecutionContext(() -> connection.setAutoCommit(true));
        }

    }

    public List<OrderDTO> searchOrders(String query) throws FailedOperationException {
        return orderDAO.searchOrders(query);
    }

    public long getSearchOrdersCount(String query) throws SQLException {
        return orderDAO.getSearchOrderCount(query);
    }

    public List<OrderDTO> searchOrders(String query, int page, int size) throws FailedOperationException {
        return orderDAO.searchOrders(query, page, size);
    }

    public OrderDTO searchOrder(String orderId) throws NotFoundException, FailedOperationException {
        List<OrderDetailDTO> orderDetails = findOrderDetails(orderId);
        List<OrderDTO> orderDTOS = searchOrders(orderId);
        orderDTOS.get(0).setOrderDetails(orderDetails);
        return orderDTOS.get(0);
    }

    public List<OrderDetailDTO> findOrderDetails(String orderId) throws NotFoundException, FailedOperationException {
        return orderDAO.findOrderDetails(orderId);
    }

    public String generateNewOrderId() throws FailedOperationException {
        String id = orderDAO.getLastOrderId();
        if (id != null) {
            return String.format("OD%03d", (Integer.parseInt(id.replace("OD", "")) + 1));
        } else {
            return "OD001";
        }

    }

    private void failedOperationExecutionContext(ExecutionContext context) throws FailedOperationException {
        try {
            context.execute();
        } catch (SQLException e) {
            throw new FailedOperationException("Failed to save the order", e);
        }
    }

    @FunctionalInterface
    interface ExecutionContext {
        void execute() throws SQLException;
    }

}
